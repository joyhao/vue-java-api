package com.joykit.modular.video.mapping;

import com.joykit.modular.video.po.VideoPO;
import com.joykit.modular.video.vo.VideoVO;
import org.springframework.stereotype.Component;

@Component
public class VideoMapping {
    public VideoVO poToVo(VideoPO po, String thumbnailUrl, String videoUrl) {
        return new VideoVO(
                po.getId(),
                po.getName(),
                po.getFileSize() == null ? 0 : po.getFileSize(),
                po.getDurationSeconds(),
                thumbnailUrl,
                videoUrl);
    }
}
