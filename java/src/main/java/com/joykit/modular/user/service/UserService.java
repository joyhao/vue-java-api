package com.joykit.modular.user.service;

import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import com.joykit.modular.user.vo.UserVO;
import com.joykit.modular.user.vo.UserDeleteVO;
import com.joykit.modular.user.dto.UserQueryDTO;
import com.joykit.modular.user.dto.UserSaveDTO;
import java.util.List;

public interface UserService {
    Result<PageResult<UserVO>> findPage(UserQueryDTO dto);
    Result<UserVO> findById(long id);
    Result<UserVO> create(UserSaveDTO dto);
    Result<UserVO> update(long id, UserSaveDTO dto);
    Result<UserDeleteVO> delete(long id);
    Result<UserDeleteVO> deleteBatch(List<Long> ids);
    Result<UserDeleteVO> deleteAll(String confirmation);
}
