package com.joykit.modular.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.joykit.modular.user.po.UserPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}
