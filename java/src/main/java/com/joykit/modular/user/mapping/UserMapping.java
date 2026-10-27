package com.joykit.modular.user.mapping;

import com.joykit.modular.user.dto.UserSaveDTO;
import com.joykit.modular.user.po.UserPO;
import com.joykit.modular.user.vo.UserVO;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapping {
    public UserPO dtoToPo(UserSaveDTO dto) {
        UserPO po = new UserPO();
        po.setName(dto.getName().trim());
        po.setAvatarUrl(trim(dto.getAvatarUrl()));
        po.setEmail(trim(dto.getEmail()));
        po.setPhone(trim(dto.getPhone()));
        po.setStatus(dto.getStatus());
        return po;
    }

    public UserVO poToVo(UserPO po) {
        UserVO vo = new UserVO();
        vo.setId(po.getId());
        vo.setName(po.getName());
        vo.setAvatarUrl(po.getAvatarUrl());
        vo.setEmail(po.getEmail());
        vo.setPhone(po.getPhone());
        vo.setStatus(po.getStatus());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public List<UserVO> poToVo(List<UserPO> users) {
        return users.stream().map(this::poToVo).toList();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
