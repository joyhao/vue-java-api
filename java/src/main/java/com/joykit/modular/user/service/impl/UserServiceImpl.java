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
    /** 全部删除时每批的行数，用来控制单个事务的大小和持锁时间 */
    private static final int DELETE_ALL_BATCH_SIZE = 5000;
    /** 深分页会让 OFFSET 扫过并丢弃前面所有行，页码超限直接拒绝。10 万页足够覆盖百万行 x 每页 20 条的场景 */
    private static final int MAX_PAGE_INDEX = 100_000;
    /** trigram 索引能生效的最短关键词长度（字符数） */
    private static final int TRIGRAM_MIN_CHARS = 3;
    /** 短关键词搜索的单条语句超时：这类搜索没有索引可用 */
    private static final int SHORT_KEYWORD_TIMEOUT_MS = 5_000;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result<PageResult<UserVO>> findPage(UserQueryDTO dto) {
        if (dto.getPageIndex() == null || dto.getPageSize() == null
                || dto.getPageIndex() < 1 || dto.getPageIndex() > MAX_PAGE_INDEX
                || dto.getPageSize() < 1 || dto.getPageSize() > 1000) {
            throw new BusinessException(BusinessException.Kind.VALIDATION,
                    "页码必须在 1 到 " + MAX_PAGE_INDEX + " 之间，每页条数必须在 1 到 1000 之间");
        }
        String keyword = dto.getKeyword() == null ? "" : dto.getKeyword().trim();
        if (keyword.length() > 100) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "搜索关键词最多 100 字");
        }
        boolean hasKeyword = StringUtils.hasText(keyword);
        // trigram 索引至少要 3 个字符才拆得出三元组；更短的关键词只能顺序扫描
        boolean indexed = keyword.codePointCount(0, keyword.length()) >= TRIGRAM_MIN_CHARS;
        boolean withCount = !Boolean.FALSE.equals(dto.getWithCount());
        long cap = (long) MAX_PAGE_INDEX * dto.getPageSize();
        long offset = (long) (dto.getPageIndex() - 1) * dto.getPageSize();
        List<UserPO> records;
        Long total = null;
        if (hasKeyword && indexed) {
            String pattern = likePattern(keyword);
            records = userMapper.searchByKeyword(pattern, offset, dto.getPageSize());
            if (withCount) total = Math.min(userMapper.countKeywordMatches(pattern, cap + 1), cap);
        } else if (hasKeyword) {
            // 短关键词：没有索引可用，限制单条语句的执行时间，防止拖垮数据库
            userMapper.setStatementTimeout(SHORT_KEYWORD_TIMEOUT_MS);
            String pattern = likePattern(keyword);
            var query = new LambdaQueryWrapper<UserPO>()
                    .and(condition -> condition.apply("name ILIKE {0} ESCAPE '!'", pattern)
                        .or().apply("email ILIKE {0} ESCAPE '!'", pattern)
                        .or().apply("phone ILIKE {0} ESCAPE '!'", pattern))
                    .orderByDesc(UserPO::getId);
            // selectCount 关闭：总数在下面单独算
            Page<UserPO> page;
            try {
                page = userMapper.selectPage(new Page<>(dto.getPageIndex(), dto.getPageSize(), false), query);
            } catch (org.springframework.dao.DataAccessException exception) {
                if (!isStatementTimeout(exception)) throw exception;
                throw new BusinessException(BusinessException.Kind.VALIDATION,
                        "没有在限定时间内搜完，请输入更完整的关键词");
            }
            records = page.getRecords();
            // 短关键词的精确计数是全表扫描，只给下界：够前端显示“下一页”即可，翻页时下界跟着增长
            if (withCount) total = offset + records.size() + (records.size() == dto.getPageSize() ? 1 : 0);
        } else {
            // 固定用按实际参数生成的定制计划。通用计划不知道 OFFSET 的值，会改成
            // “全表扫描 + 哈希连接”，同一语句从 160ms 退化到 10 秒以上
            userMapper.setLocal("plan_cache_mode", "force_custom_plan");
            records = userMapper.pageByOffset(offset, dto.getPageSize());
            if (withCount) total = estimatedTotal(dto.getPageSize());
        }
        return Result.success(new PageResult<>(total, userMapping.poToVo(records)));
    }

    /**
     * 服务端 statement_timeout 触发时返回 SQLSTATE 57014（语句被取消），
     * Spring 不会把它翻译成 QueryTimeoutException，所以沿异常链找这个状态码。
     */
    private static boolean isStatementTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.dao.QueryTimeoutException) return true;
            if (cause instanceof java.sql.SQLException sql && "57014".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    /** 把用户输入转成 LIKE 模式，转义通配符，避免 “%” “_” 被当成通配符 */
    private static String likePattern(String keyword) {
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /**
     * 无关键字时的总数：统计信息估算（纳秒级），估算不可用时退回精确计数。
     * 展示上限封顶：超过页码上限之外的总数对翻页没有意义，
     * 不封顶的话前端会生成点了就 400 的页码按钮。
     */
    private Long estimatedTotal(int pageSize) {
        long estimate = userMapper.estimateTotal();
        if (estimate < 0) return userMapper.selectCount(new LambdaQueryWrapper<>());
        return Math.min(estimate, (long) MAX_PAGE_INDEX * pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public Result<UserVO> findById(long id) {
        return Result.success(userMapping.poToVo(requireUser(id)));
    }

    @Override
    @Transactional
    public Result<UserVO> create(UserSaveDTO dto) {
        requirePhoneAvailable(dto.getPhone(), null);
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
        requirePhoneAvailable(dto.getPhone(), id);
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
    public Result<UserDeleteVO> deleteAll(String confirmation) {
        if (!"删除全部用户".equals(confirmation)) {
            throw new BusinessException(BusinessException.Kind.VALIDATION, "请输入“删除全部用户”确认操作");
        }
        // 分批删除，每批各自提交：不要给这个方法加 @Transactional，否则会合并回一个大事务。
        // 中途失败时已删除的批次不会回滚，接口返回已删除的行数，可以重新执行。
        int deleted = 0;
        int count;
        do {
            count = userMapper.deleteChunk(DELETE_ALL_BATCH_SIZE);
            deleted += count;
        } while (count > 0);
        return Result.success(new UserDeleteVO(deleted));
    }

    /**
     * 表按 id 哈希分区后 phone 的全局唯一约束无法保留（唯一索引必须包含分区键），
     * 查重移到应用层；update 时排除自己。
     */
    private void requirePhoneAvailable(String phone, Long excludeId) {
        if (!StringUtils.hasText(phone)) return;
        var query = new LambdaQueryWrapper<UserPO>().eq(UserPO::getPhone, phone);
        if (excludeId != null) query.ne(UserPO::getId, excludeId);
        Long count = userMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(BusinessException.Kind.CONFLICT, "联系电话已被其他用户使用");
        }
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
