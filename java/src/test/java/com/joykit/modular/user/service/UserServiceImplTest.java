package com.joykit.modular.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.joykit.common.exception.BusinessException;
import com.joykit.common.result.PageResult;
import com.joykit.modular.user.dto.UserQueryDTO;
import com.joykit.modular.user.mapper.UserMapper;
import com.joykit.modular.user.mapping.UserMapping;
import com.joykit.modular.user.po.UserPO;
import com.joykit.modular.user.service.impl.UserServiceImpl;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class UserServiceImplTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserServiceImpl service = new UserServiceImpl(userMapper, new UserMapping());
    private final AtomicReference<Page<UserPO>> requestedPage = new AtomicReference<>();

    @BeforeEach
    void echoRequestedPage() {
        when(userMapper.selectPage(any(), any())).thenAnswer(call -> {
            Page<UserPO> page = call.getArgument(0);
            requestedPage.set(page);
            UserPO user = new UserPO();
            user.setId(1L);
            page.setRecords(List.of(user));
            page.setTotal(1010007);
            return page;
        });
        when(userMapper.estimateTotal()).thenReturn(1010007L);
        when(userMapper.searchByKeyword(anyString(), anyLong(), anyInt()))
                .thenAnswer(call -> List.of(new UserPO()));
        UserPO listed = new UserPO();
        listed.setId(1L);
        when(userMapper.pageByOffset(anyLong(), anyInt())).thenAnswer(call -> List.of(listed));
    }

    @Test
    void countsTotalByDefault() {
        // 估算值低于展示上限（100000 页 x 10 条/页）时原样返回
        when(userMapper.estimateTotal()).thenReturn(999_999L);
        PageResult<?> result = service.findPage(new UserQueryDTO()).getData();

        assertEquals(999_999L, result.getTotal());
        // 总数由 estimateTotal 提供，取页走延迟关联，都不做精确 COUNT
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    void countsTotalWhenExplicitlyRequested() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setWithCount(true);
        when(userMapper.estimateTotal()).thenReturn(999_999L);

        assertEquals(999_999L, service.findPage(dto).getData().getTotal());
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    void keywordOfThreeOrMoreCharsUsesIndexedSearchAndExactCount() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword("王伟强");
        when(userMapper.countKeywordMatches(anyString(), anyLong())).thenReturn(42L);

        assertEquals(42L, service.findPage(dto).getData().getTotal());
        verify(userMapper).searchByKeyword(anyString(), eq(0L), eq(10));
    }

    @Test
    void keywordCountIsCappedAtPageLimit() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword("王伟强");
        dto.setPageSize(20);
        // 上亿行时高频词的总数只数到展示上限
        when(userMapper.countKeywordMatches(anyString(), anyLong())).thenReturn(2_000_001L);

        assertEquals(2_000_000L, service.findPage(dto).getData().getTotal());
    }

    @Test
    void shortKeywordSkipsFullTableCountAndReturnsLowerBound() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword("王");

        // 1~2 个字符用不上 trigram 索引：不数总数，只给“至少这么多条”的下界
        assertEquals(1L, service.findPage(dto).getData().getTotal());
        verify(userMapper, never()).countKeywordMatches(anyString(), anyLong());
        verify(userMapper, never()).searchByKeyword(anyString(), anyLong(), anyInt());
        verify(userMapper).setStatementTimeout(anyInt());
    }

    @Test
    void shortKeywordTimeoutIsReportedAsValidationError() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword("王");
        // 用 doThrow 打桩：when(...) 会先用 null 参数触发已有的 Answer
        org.mockito.Mockito.doThrow(new org.springframework.dao.QueryTimeoutException("canceling statement"))
                .when(userMapper).selectPage(any(), any());

        var failure = assertThrows(BusinessException.class, () -> service.findPage(dto));

        assertEquals(BusinessException.Kind.VALIDATION, failure.kind());
    }

    @Test
    void serverSideStatementTimeoutIsAlsoReportedAsValidationError() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword("王");
        // 服务端 statement_timeout 触发时是 SQLSTATE 57014，Spring 不会翻译成 QueryTimeoutException
        org.mockito.Mockito.doThrow(new org.mybatis.spring.MyBatisSystemException(
                        new java.sql.SQLException("canceling statement due to statement timeout", "57014")))
                .when(userMapper).selectPage(any(), any());

        var failure = assertThrows(BusinessException.class, () -> service.findPage(dto));

        assertEquals(BusinessException.Kind.VALIDATION, failure.kind());
    }

    @Test
    void otherDatabaseErrorsAreNotDisguisedAsTimeout() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setKeyword("王");
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("connection lost"))
                .when(userMapper).selectPage(any(), any());

        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, () -> service.findPage(dto));
    }

    @Test
    void estimatedTotalIsCappedToPageLimit() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setPageSize(10);
        when(userMapper.estimateTotal()).thenReturn(999_999_999L);

        // 展示上限 = 100000 页 x 每页 10 条，防止前端生成点了就 400 的页码
        assertEquals(1_000_000L, service.findPage(dto).getData().getTotal());
    }

    @Test
    void missingStatsFallBackToExactCount() {
        when(userMapper.estimateTotal()).thenReturn(-1L);
        when(userMapper.selectCount(any())).thenReturn(7L);

        assertEquals(7L, service.findPage(new UserQueryDTO()).getData().getTotal());
    }

    @Test
    void pageChangeCanSkipExpensiveCount() throws Exception {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setPageIndex(2);
        dto.setWithCount(false);

        PageResult<?> result = service.findPage(dto).getData();

        assertNull(result.getTotal());
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result).contains("\"total\":null"));
        assertEquals(1, result.getList().size());
        // 第 2 页、每页 10 条 → 偏移 10 行，走延迟关联而不是 MyBatis-Plus 的 selectPage
        verify(userMapper).pageByOffset(10L, 10);
        // 通用计划会让这条语句退化成全表扫描，必须固定为定制计划
        verify(userMapper).setLocal("plan_cache_mode", "force_custom_plan");
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    void rejectsTooDeepPaginationBeforeQuerying() {
        UserQueryDTO dto = new UserQueryDTO();
        dto.setPageIndex(100_001);

        var failure = assertThrows(BusinessException.class, () -> service.findPage(dto));

        assertEquals(BusinessException.Kind.VALIDATION, failure.kind());
        verify(userMapper, never()).selectPage(any(), any());
        verify(userMapper, never()).pageByOffset(anyLong(), anyInt());
    }

    @Test
    void deleteAllDeletesInBatchesUntilTableIsEmpty() {
        when(userMapper.deleteChunk(5000)).thenReturn(5000, 5000, 345, 0);

        assertEquals(10345, service.deleteAll("删除全部用户").getData().deletedCount());
        verify(userMapper, times(4)).deleteChunk(5000);
    }

    @Test
    void deleteAllRejectsWrongConfirmationBeforeDeletingAnything() {
        var failure = assertThrows(BusinessException.class, () -> service.deleteAll("删错了"));

        assertEquals(BusinessException.Kind.VALIDATION, failure.kind());
        verify(userMapper, never()).deleteChunk(anyInt());
    }

    @Test
    void deleteAllMustNotRunInOneTransaction() throws Exception {
        // 每批各自提交才有效果；加上 @Transactional 会把所有批次合并回一个大事务。
        assertNull(UserServiceImpl.class.getMethod("deleteAll", String.class).getAnnotation(Transactional.class));
    }
}
