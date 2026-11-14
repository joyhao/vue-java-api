package com.joykit.modular.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.joykit.common.exception.BusinessException;
import com.joykit.common.result.PageParams;
import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import com.joykit.modular.video.mapper.VideoMapper;
import com.joykit.modular.video.mapping.VideoMapping;
import com.joykit.modular.video.po.VideoPO;
import com.joykit.modular.video.vo.VideoVO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
            @Value("${app.video.public-base-url:http://localhost:9528/media/}") String publicBaseUrl) {
        this.videoMapper = videoMapper;
        this.videoMapping = videoMapping;
        this.thumbnailDirectory = Paths.get(thumbnailDirectory).toAbsolutePath().normalize();
        this.ffprobeExecutable = ffprobeExecutable;
        this.ffmpegExecutable = ffmpegExecutable;
        this.videoRoot = Paths.get(videoRoot).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    }

    @Transactional(readOnly = true)
    public Result<PageResult<VideoVO>> findPage(PageParams params) {
        if (params.getPageIndex() == null || params.getPageSize() == null
                || params.getPageIndex() < 1 || params.getPageSize() < 1 || params.getPageSize() > 1000) {
            throw new BusinessException(BusinessException.Kind.VALIDATION,
                    "页码必须大于 0，每页条数必须在 1 到 1000 之间");
        }
        String keyword = params.getKeyword() == null ? "" : params.getKeyword().trim();
        var query = new LambdaQueryWrapper<VideoPO>();
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            query.apply("name ILIKE {0} ESCAPE '!'", pattern);
        }
        query.orderByAsc(VideoPO::getRelativePath);
        Page<VideoPO> page = videoMapper.selectPage(
                new Page<>(params.getPageIndex(), params.getPageSize()), query);
        return Result.success(PageResult.of(page.getTotal(),
                page.getRecords().stream().map(this::toVo).toList()));
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
        return videoMapping.poToVo(po,
                urlFor("/api/videos/" + po.getId() + "/thumbnail"),
                publicBaseUrl + UriUtils.encodePath(relative, StandardCharsets.UTF_8));
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

    private Double probeDuration(Path video) {
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
