package com.joykit.modular.video.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.joykit.common.result.PageParams;
import com.joykit.modular.video.mapper.VideoMapper;
import com.joykit.modular.video.mapping.VideoMapping;
import com.joykit.modular.video.po.VideoPO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @TempDir
    Path tempDirectory;

    @Mock
    VideoMapper videoMapper;

    private VideoService service;

    @BeforeEach
    void setUp() throws Exception {
        Path videos = Files.createDirectory(tempDirectory.resolve("videos"));
        service = new VideoService(
                videoMapper,
                new VideoMapping(),
                videos.toString(),
                tempDirectory.resolve("thumbnails").toString(),
                "missing-ffprobe",
                "missing-ffmpeg",
                "http://media.test/videos/");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(9007);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void findPageReadsFromDatabase() {
        VideoPO po = new VideoPO();
        po.setId("4d7bdb7f-d6c5-3b74-a9ce-d02b482b3351");
        po.setName("alpha.mp4");
        po.setRelativePath("alpha.mp4");
        po.setFileSize(1024L);
        po.setDurationSeconds(12.5);
        Page<VideoPO> page = new Page<>(1, 10);
        page.setRecords(List.of(po));
        page.setTotal(1);
        when(videoMapper.selectPage(any(), any())).thenReturn(page);

        PageParams params = new PageParams();
        params.setPageIndex(1);
        params.setPageSize(10);
        var result = service.findPage(params).getData();

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("alpha.mp4", result.getList().getFirst().name());
        assertEquals(1024, result.getList().getFirst().size());
        assertEquals(12.5, result.getList().getFirst().durationSeconds());
        assertTrue(result.getList().getFirst().thumbnailUrl()
                .startsWith("http://localhost:9007/api/videos/"));
        assertEquals("http://media.test/videos/alpha.mp4",
                result.getList().getFirst().videoUrl());
    }
}
