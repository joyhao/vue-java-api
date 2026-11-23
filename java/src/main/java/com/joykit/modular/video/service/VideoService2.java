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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VideoService2 {
    private static final Logger LOG = LoggerFactory.getLogger(VideoService2.class);
    private static final Set<String> SKIPPED_DIRECTORY_NAMES = Set.of(
            "system volume information", "$recycle.bin", "recycler", "config.msi", "recovery", ".trashes");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "m4v", "mov", "avi", "mkv", "wmv", "flv", "webm", "mpg", "mpeg", "3gp",
            "ts", "mts", "m2ts", "ogv", "vob", "asf", "mxf");
    private final VideoMapper videoMapper;
    private final VideoMapping videoMapping;
    private final Path videoRoot;
    private final Path thumbnailDirectory;
    private final String publicBaseUrl;
    private final VideoService videoService;

    public VideoService2(
            VideoMapper videoMapper,
            VideoMapping videoMapping,
            VideoService videoService,
            @Value("${app.video.root}") String videoRoot,
            @Value("${app.video.thumbnail-directory}") String thumbnailDirectory,
            @Value("${app.video.public-base-url}") String publicBaseUrl
    ) {
        this.videoService= videoService;
        this.videoMapper = videoMapper;
        this.videoMapping = videoMapping;
        this.videoRoot = Paths.get(videoRoot).normalize();
        this.thumbnailDirectory = Paths.get(thumbnailDirectory).normalize();
        this.publicBaseUrl = publicBaseUrl;
    }


    @Transactional(readOnly = true)
    public Result<PageResult<VideoVO>> findPage(@RequestBody PageParams params) {
        Integer pageIndex = params.getPageIndex();
        Integer pageSize = params.getPageSize();
        String keyword = params.getKeyword();
        var query = new LambdaQueryWrapper<VideoPO>();
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            query.apply("name ILIKE {0} ESCAPE '!'", pattern);
        }

        query.orderByAsc(VideoPO::getName);

        Page<VideoPO> page = new Page<>(pageIndex, pageSize);
        page = videoMapper.selectPage(page, query);
        List<VideoVO> list = page.getRecords().stream().map(this::toVo).toList();
        long total = page.getTotal();
        return Result.success(PageResult.of1(total, list));
    }

    @Transactional
    public Result<Object> sync() {
        // 扫描视频目录,得到所有视频的绝对路径
        List<Path> files = scanVideos();
        Instant now = Instant.now();


        var query = new LambdaQueryWrapper<VideoPO>();
        List<VideoPO> list = videoMapper.selectList(query);
        Map<String, VideoPO> existing = list.stream().collect(Collectors.toMap(VideoPO::getId, po -> po));

        long inserted = 0;
        long updated = 0;

        // 遍历磁盘上的文件
        for (Path path : files) {
            String relative = videoRoot.relativize(path).toString();
            String id = idFor(relative);
            String name = path.getFileName().toString();
            long size = 0;
            try {
                size = Files.size(path);
            } catch (IOException exception) {
                LOG.warn("无法读取文件大小 {}: {}", path, exception.getMessage());
            }

            // 移除缓存的数据
            VideoPO po = existing.remove(id);
            // po null 说明缓存和数据库没有这条数据
            if (po == null) {
                po = new VideoPO();
                po.setId(id);
                po.setName(name);
                po.setRelativePath(relative);
                po.setFileSize(size);
                po.setCreatedAt(now);
                po.setCreatedAt(now);
                po.setUpdatedAt(now);
                po.setSyncedAt(now);
                videoMapper.insert(po);
                inserted++;
            } else {
                boolean changed = !name.equals(po.getName()) || !relative.equals(po.getRelativePath()) || size != po.getFileSize();
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

        long removed = existing.isEmpty() ? 0 : videoMapper.deleteBatchIds(existing.keySet());
        Long total = videoMapper.selectCount(new LambdaQueryWrapper<>());
        LOG.info("视频同步完成: 扫描 {}, 新增 {}, 更新 {}, 移除 {}, 库内 {}",
                files.size(), inserted, updated, removed, total);
        return Result.success(new VideoSyncVO(
                files.size(),
                inserted,
                updated,
                removed,
                total == null ? 0 : total));
    }


    @Transactional
    public Double resolveDuration(String id) {
        VideoPO po = videoMapper.selectById(id);
        if (po == null) {
            throw new BusinessException(BusinessException.Kind.NOT_FOUND, "视频不存在");
        }

        if (po.getDurationSeconds() != null) {
            return po.getDurationSeconds();
        }

        Path path = Path.of(po.getRelativePath());

        Double duration = videoService.probeDuration(path);
        if (duration != null){
            po.setDurationSeconds(duration);
            po.setUpdatedAt(Instant.now());
            videoMapper.updateById(po);
        }

        return duration;
    }


    private static String idFor(String relative) {
        return UUID.nameUUIDFromBytes(relative.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private List<Path> scanVideos() {
        if (!Files.isDirectory(videoRoot)) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "视频目录不存在或者不是目录:" + videoRoot);
        }
        List<Path> paths = new ArrayList<>();
        collectVideos(videoRoot, paths);
        paths.sort(Comparator.comparing(path -> {
            return videoRoot.relativize(path).toString();
        }, String.CASE_INSENSITIVE_ORDER));

        return paths;
    }


    private void collectVideos(Path directory, List<Path> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!isSkippedDirectory(path)) {
                        collectVideos(path, result);
                    }
                } else if (Files.isRegularFile(path) && isVideo(path)) {
                    result.add(path.toAbsolutePath().normalize());
                }
            }
        } catch (IOException | DirectoryIteratorException | SecurityException exception) {
            LOG.warn("跳过无法读取的目录 {}: {}", directory, exception.getMessage());
        }

    }

    private boolean isVideo(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf(".");
        return dot >= 0 && VIDEO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }


    private boolean isSkippedDirectory(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SKIPPED_DIRECTORY_NAMES.contains(name);
    }

    private String urlFor(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
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
}
