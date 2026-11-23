package com.joykit.modular.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.joykit.common.constants.ApiConstants;
import com.joykit.common.exception.BusinessException;
import com.joykit.common.result.PageParams;
import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import com.joykit.modular.video.mapper.VideoMapper;
import com.joykit.modular.video.mapping.VideoMapping;
import com.joykit.modular.video.po.VideoPO;
import com.joykit.modular.video.vo.VideoSyncVO;
import com.joykit.modular.video.vo.VideoVO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Service
public class VideoService {

    private static final Logger LOG = LoggerFactory.getLogger(VideoService.class);
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "m4v", "mov", "avi", "mkv", "wmv", "flv", "webm", "mpg", "mpeg", "3gp",
            "ts", "mts", "m2ts", "ogv", "vob", "asf", "mxf");
    private static final Set<String> SKIPPED_DIRECTORY_NAMES = Set.of(
            "system volume information", "$recycle.bin", "recycler", "config.msi", "recovery", ".trashes");

    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final long FFMPEG_TIMEOUT_SECONDS = 30;

    private final VideoMapper videoMapper;
    private final VideoMapping videoMapping;
    private final Path thumbnailDirectory;
    private final String ffprobeExecutable;
    private final String ffmpegExecutable;
    private final Path videoRoot;
    private final String publicBaseUrl;

    public VideoService(
            VideoMapper videoMapper,
            VideoMapping videoMapping,
            @Value("${app.video.root}") String videoRoot,
            @Value("${app.video.thumbnail-directory}") String thumbnailDirectory,
            @Value("${app.video.ffprobe-executable}") String ffprobeExecutable,
            @Value("${app.video.ffmpeg-executable}") String ffmpegExecutable,
            @Value("${app.video.public-base-url}") String publicBaseUrl) {
        this.videoMapper = videoMapper;
        this.videoMapping = videoMapping;
        this.videoRoot = Paths.get(videoRoot).normalize();
        this.thumbnailDirectory = Paths.get(thumbnailDirectory).toAbsolutePath().normalize();
        this.ffprobeExecutable = ffprobeExecutable;
        this.ffmpegExecutable = ffmpegExecutable;
        this.publicBaseUrl = publicBaseUrl;
    }


    @Transactional(readOnly = true)
    public Result<PageResult<VideoVO>> findPage(PageParams params) {
        String keyword = params.getKeyword();
        Integer page = params.getPageIndex();
        Integer size = params.getPageSize();
        var query = new LambdaQueryWrapper<VideoPO>();

        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            query.apply("name ILIKE {0} ESCAPE '!'", pattern);
        }

        query.orderByAsc(VideoPO::getName);

        Page<VideoPO> _page = videoMapper.selectPage(new Page<>(page, size), query);
        List<VideoPO> records = _page.getRecords();
        List<VideoVO> list = records.stream().map(this::toVo).toList();
        long total = _page.getTotal();
        return Result.success(PageResult.of1(total, list));
    }

    /**
     * 同步视频目录到数据库（增量同步，不是每次全量重建）。
     * <p>
     * 整体思路：
     * 1. 扫描根目录，拿到所有视频文件的绝对路径（scanVideos）；
     * 2. 把库里现有记录读进 Map，key 是视频 id；
     * 3. 逐个文件处理：
     * - Map 里没有对应 id → 新文件，insert；
     * - Map 里有 → 已存在，对比字段，有变化就更新 updatedAt；无论变没变都刷新 syncedAt；
     * - 处理完就从 Map 里 remove 掉，表示“这个文件还在磁盘上”；
     * 4. 循环结束，Map 里剩下的就是“磁盘上已经没了”的记录 → 批量删除；
     * 5. 返回统计结果。
     *
     * @Transactional：整个方法是一个事务，要么全部成功提交，要么失败整体回滚， 不会出现“加了一半、删了一半”的中间状态。
     */
    @Transactional
    public Result<VideoSyncVO> sync() {
        // 1. 扫描视频目录，得到所有视频文件的绝对路径
        List<Path> files = scanVideos();
        // 本次同步统一用一个时间戳，保证同一批记录的时间一致
        Instant now = Instant.now();

        // 2. 把数据库里现有记录读进 Map，方便按 id 快速查找
        //    key = 视频 id（由相对路径算出的固定 UUID），value = 数据库记录
        var query = new LambdaQueryWrapper<VideoPO>();
        // 查出全部记录后，直接用 Stream 收集成 Map，用视频 id 作为 key
        Map<String, VideoPO> existing = videoMapper.selectList(query)
                .stream()
                .collect(Collectors.toMap(VideoPO::getId, po -> po));

        long inserted = 0;
        long updated = 0;
        // 3. 遍历磁盘上扫到的每个文件
        for (Path path : files) {
            // 相对根目录的路径，比如 "mv/电影.mp4"，同时用作生成 id 的依据
            String relative = videoRoot.relativize(path).toString();
            // 用相对路径生成固定 UUID：同一文件每次同步 id 都一样，才能做到“增量”
            String id = idFor(relative);
            String name = path.getFileName().toString();

            // 读取文件大小，读不到（被占用/权限）就记 0，不让整个同步失败
            long size = 0;
            try {
                size = Files.size(path);
            } catch (IOException exception) {
                LOG.warn("无法读取文件大小 {}: {}", path, exception.getMessage());
            }

            // 移除Map缓存里面对应的数据：
            VideoPO po = existing.remove(id);
            if (po == null) {
                // 新文件：构造一条记录并插入
                po = new VideoPO();
                po.setId(id);
                po.setName(name);
                po.setRelativePath(relative);
                po.setFileSize(size);
                po.setCreatedAt(now);
                po.setUpdatedAt(now);
                po.setSyncedAt(now);
                videoMapper.insert(po);
                inserted++;
            } else {
                // 旧文件：先判断关键信息有没有变（文件名/相对路径/大小）
                // null 的 fileSize 当作 0 比较，避免 NPE
                boolean changed = !name.equals(po.getName())
                        || !relative.equals(po.getRelativePath())
                        || size != (po.getFileSize() == null ? 0 : po.getFileSize());
                // 无论有没有变化，都把最新值写回，并刷新同步时间
                po.setName(name);
                po.setRelativePath(relative);
                po.setFileSize(size);
                po.setSyncedAt(now);
                if (changed) {
                    // 只有真正变化才算“更新”，并刷新 updatedAt
                    po.setUpdatedAt(now);
                    updated++;
                }
                videoMapper.updateById(po);
            }
        }

        // 4. 循环结束后 Map 里剩下的，都是“库里有、但磁盘上找不到”的记录 → 删除
        long removed = existing.isEmpty() ? 0 : videoMapper.deleteBatchIds(existing.keySet());

        // 5. 查一下同步后库里总数，打日志 + 返回结果给前端展示
        Long total = videoMapper.selectCount(new LambdaQueryWrapper<>());
        LOG.info("视频同步完成: 扫描 {}, 新增 {}, 更新 {}, 移除 {}, 库内 {}",
                files.size(), inserted, updated, removed, total);
        return Result.success(
                new VideoSyncVO(
                    files.size(),
                    inserted,
                    updated,
                    removed,
                    total == null ? 0 : total
                ));
    }

    /**
     * 扫描视频根目录，返回所有视频文件的绝对路径（已排序）。
     * 1. 先校验配置的根目录存在且是目录；
     * 2. 调用 collectVideos 递归收集；
     * 3. 按“相对根目录的路径”做不区分大小写的排序，保证每次同步顺序稳定。
     */
    private List<Path> scanVideos() {

        if (!Files.isDirectory(videoRoot)) {
            throw new BusinessException(BusinessException.Kind.VALIDATION,
                    "视频目录不存在或不是目录: " + videoRoot);
        }
        List<Path> files = new ArrayList<>();
        collectVideos(videoRoot, files);
        files.sort(Comparator.comparing(path -> {
                    return videoRoot.relativize(path).toString();
                },
                String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    /**
     * 递归遍历一个目录，把里面的视频文件追加到 result。
     * <p>
     * 遍历规则：
     * - 子目录：若不在跳过名单里，递归进去继续找（深度优先）；
     * - 普通文件：扩展名在视频白名单里才收集；
     * - 读不了的目录（权限/被占用等）：只记 warn 日志，跳过，不让整个同步失败。
     * <p>
     * 参数：
     *
     * @param directory 当前要遍历的目录
     * @param result    收集结果的容器，递归过程共用同一个 List
     */
    private void collectVideos(Path directory, List<Path> result) {
        // try-with-resources：DirectoryStream 是“懒加载”的目录句柄，遍历完必须 close，
        // 写在 try(...) 里会自动关闭，不用手动 finally。
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                // NOFOLLOW_LINKS：判断“是不是目录”时不跟随符号链接，
                // 避免链接指回上层造成无限递归（目录环）。
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!isSkippedDirectory(path)) {
                        collectVideos(path, result);   // 递归：进入子目录
                    }
                } else if (Files.isRegularFile(path) && isVideo(path)) {
                    // 绝对路径 + normalize 去掉 ".."、".", 保证路径唯一、可用于算相对路径
                    result.add(path.toAbsolutePath().normalize());
                }
            }
            // DirectoryIteratorException：遍历过程中目录流本身出错（如目录被删）；
            // SecurityException：没有读权限。两者都跳过，不中断整个同步。
        } catch (IOException | DirectoryIteratorException | SecurityException exception) {
            LOG.warn("跳过无法读取的目录 {}: {}", directory, exception.getMessage());
        }
    }

    /**
     * 判断目录是否应该跳过：系统目录 + 配置里 excluded-directories 指定的目录。
     * 目录名统一转小写再比较，避免大小写差异漏掉。
     */
    private boolean isSkippedDirectory(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SKIPPED_DIRECTORY_NAMES.contains(name);
    }

    /**
     * 判断文件是不是视频：取最后一个 '.' 后面的扩展名，
     * 转小写后看是否在视频扩展名白名单 VIDEO_EXTENSIONS 里。
     */
    private static boolean isVideo(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && VIDEO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public Path resolveVideo(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw notFound();
        }
        VideoPO po = videoMapper.selectById(id);
        if (po == null || po.getRelativePath() == null) {
            throw notFound();
        }
        Path path = videoRoot.resolve(po.getRelativePath()).toAbsolutePath().normalize();
        if (!path.startsWith(videoRoot) || !Files.isRegularFile(path)) {
            throw new BusinessException(BusinessException.Kind.NOT_FOUND, "视频文件不存在或已被移动");
        }
        return path;
    }

    public Path resolveThumbnail(String id) {
        Path video = resolveVideo(id);
        Path thumbnail = thumbnailPath(video);
        if (!Files.isRegularFile(thumbnail)) {
            generateThumbnail(video, thumbnail);
        }
        return thumbnail;
    }

    @Transactional
    public Double resolveDuration(String id) {
        VideoPO po = videoMapper.selectById(id);
        if (po == null) {
            throw notFound();
        }
        if (po.getDurationSeconds() != null) {
            return po.getDurationSeconds();
        }
        Double duration = probeDuration(resolveVideo(id));
        if (duration != null) {
            VideoPO update = new VideoPO();
            update.setId(id);
            update.setDurationSeconds(duration);
            update.setUpdatedAt(Instant.now());
            videoMapper.updateById(update);
        }
        return duration;
    }

    private VideoVO toVo(VideoPO po) {
        String relative = po.getRelativePath().replace(java.io.File.separatorChar, '/');
        String thumbnailUrl = urlFor(ApiConstants.API_PREFIX + "/videos/" + po.getId() + "/thumbnail");
        String videoUrl = publicBaseUrl + UriUtils.encodePath(relative, StandardCharsets.UTF_8);
        return videoMapping.poToVo(
                po,
                thumbnailUrl,
                videoUrl);
    }

    private static String idFor(String relative) {
        return UUID.nameUUIDFromBytes(relative.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Path thumbnailPath(Path video) {
        return thumbnailDirectory.resolve(idFor(videoRoot.relativize(video).toString()) + ".jpg");
    }

    private String urlFor(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
    }

    private static BusinessException notFound() {
        return new BusinessException(BusinessException.Kind.NOT_FOUND, "视频文件不存在");
    }

    public Double probeDuration(Path video) {
        try {
            Process process = new ProcessBuilder(
                    ffprobeExecutable,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    video.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || output.isEmpty()) {
                return null;
            }
            return Double.parseDouble(output.lines().findFirst().orElseThrow());
        } catch (IOException exception) {
            LOG.warn("FFprobe 无法读取 {}: {}", video, exception.getMessage());
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void generateThumbnail(Path video, Path thumbnail) {
        try {
            Files.createDirectories(thumbnailDirectory);
            Path tempFile = Files.createTempFile(thumbnailDirectory, "thumb-", ".jpg");
            Process process = new ProcessBuilder(
                    ffmpegExecutable,
                    "-hide_banner", "-loglevel", "error", "-y",
                    "-ss", "00:00:05",
                    "-i", video.toString(),
                    "-frames:v", "1",
                    "-vf", "scale=480:-2",
                    tempFile.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Files.deleteIfExists(tempFile);
                throw new BusinessException(BusinessException.Kind.VALIDATION, "生成视频缩略图超时");
            }
            if (process.exitValue() != 0 || Files.size(tempFile) == 0) {
                Files.deleteIfExists(tempFile);
                throw new BusinessException(BusinessException.Kind.VALIDATION,
                        "无法生成该视频的缩略图，请检查视频编码格式");
            }
            try {
                Files.move(tempFile, thumbnail, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(tempFile, thumbnail, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(BusinessException.Kind.VALIDATION,
                    "缩略图生成失败，请安装 FFmpeg 并配置 app.video.ffmpeg-executable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BusinessException.Kind.VALIDATION, "生成视频缩略图已中断");
        }
    }
}
