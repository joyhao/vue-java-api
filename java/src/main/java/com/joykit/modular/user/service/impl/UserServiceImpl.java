package com.joykit.modular.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.joykit.common.exception.BusinessException;
import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import com.joykit.modular.user.vo.UserDeleteVO;
import com.joykit.modular.user.dto.UserQueryDTO;
import com.joykit.modular.user.dto.UserSaveDTO;
import com.joykit.modular.user.mapper.UserMapper;
import com.joykit.modular.user.mapping.UserMapping;
import com.joykit.modular.user.po.UserPO;
import com.joykit.modular.user.service.UserService;
import com.joykit.modular.user.vo.UserVO;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserMapping userMapping;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result<PageResult<UserVO>> findPage(UserQueryDTO dto) {
        if (dto.getPageIndex() == null || dto.getPageSize() == null
                || dto.getPageIndex() < 1 || dto.getPageSize() < 1 || dto.getPageSize() > 1000) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "页码必须大于 0，每页条数必须在 1 到 1000 之间");
        }
        String keyword = dto.getKeyword() == null ? "" : dto.getKeyword().trim();
        if (keyword.length() > 100) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "搜索关键词最多 100 字");
        }
        var query = new LambdaQueryWrapper<UserPO>();
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            query.and(condition -> condition.apply("name ILIKE {0} ESCAPE '!'", pattern)
                .or().apply("email ILIKE {0} ESCAPE '!'", pattern)
                .or().apply("phone ILIKE {0} ESCAPE '!'", pattern));
        }
        query.orderByDesc(UserPO::getId);
        Page<UserPO> page = userMapper.selectPage(new Page<>(dto.getPageIndex(), dto.getPageSize()), query);
        return Result.success(PageResult.of(page.getTotal(), userMapping.poToVo(page.getRecords())));
    }

    @Override
    @Transactional(readOnly = true)
    public Result<UserVO> findById(long id) {
        return Result.success(userMapping.poToVo(requireUser(id)));
    }

    @Override
    @Transactional
    public Result<UserVO> create(UserSaveDTO dto) {
        UserPO po = userMapping.dtoToPo(dto);
        Instant now = Instant.now();
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        write(() -> userMapper.insert(po));
        return Result.success(userMapping.poToVo(requireUser(po.getId())));
    }

    @Override
    @Transactional
    public Result<UserVO> update(long id, UserSaveDTO dto) {
        validateId(id);
        UserPO po = userMapping.dtoToPo(dto);
        po.setId(id);
        po.setUpdatedAt(Instant.now());
        if (write(() -> userMapper.updateById(po)) == 0) throw notFound();
        return Result.success(userMapping.poToVo(requireUser(id)));
    }

    @Override
    @Transactional
    public Result<UserDeleteVO> delete(long id) {
        validateId(id);
        int count = write(() -> userMapper.deleteById(id));
        if (count == 0) throw notFound();
        return Result.success(new UserDeleteVO(count));
    }

    @Override
    @Transactional
    public Result<UserDeleteVO> deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "请选择 1 到 100 个用户");
        }
        ids.forEach(UserServiceImpl::validateId);
        var query = new LambdaQueryWrapper<UserPO>().in(UserPO::getId, ids.stream().distinct().toList());
        return Result.success(new UserDeleteVO(write(() -> userMapper.delete(query))));
    }

    @Override
    @Transactional
    public Result<UserDeleteVO> deleteAll(String confirmation) {
        if (!"删除全部用户".equals(confirmation)) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "请输入“删除全部用户”确认操作");
        }
        return Result.success(new UserDeleteVO(write(() -> userMapper.delete(new LambdaQueryWrapper<UserPO>()))));
    }

    private UserPO requireUser(long id) {
        validateId(id);
        UserPO po = userMapper.selectById(id);
        if (po == null) throw notFound();
        return po;
    }

    private static void validateId(Long id) {
        if (id == null || id < 1) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "用户 ID 必须是正整数");
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(BusinessException.Kind.NOT_FOUND, "用户不存在或已被删除");
    }

    private static <T> T write(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(BusinessException.Kind.CONFLICT, "联系电话已被其他用户使用");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(BusinessException.Kind.CONFLICT, "数据存在约束或关联，无法完成此操作");
        }
    }
}
