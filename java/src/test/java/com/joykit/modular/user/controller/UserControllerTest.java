package com.joykit.modular.user.controller;


import com.joykit.common.exception.GlobalExceptionHandler;
import com.joykit.common.filter.TraceIdFilter;
import com.joykit.common.result.Result;
import com.joykit.modular.user.controller.UserController;
import com.joykit.modular.user.service.UserService;
import com.joykit.modular.user.vo.UserDeleteVO;
import com.joykit.common.result.PageResult;
import com.joykit.modular.user.dto.UserQueryDTO;
import com.joykit.modular.user.vo.UserVO;
import com.joykit.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {
    private MockMvc mvc;
    private static final String INPUT = "{\"name\":\"测试用户\",\"email\":\"test@example.com\",\"status\":1}";

    @BeforeEach
    void setup() {
        UserService service = org.mockito.Mockito.mock(UserService.class);
        Map<Long, UserVO> users = new LinkedHashMap<>();
        long[] sequence = {0};
        org.mockito.Mockito.when(service.create(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            com.joykit.modular.user.dto.UserSaveDTO dto = call.getArgument(0);
            UserVO user = new UserVO();
            user.setId(++sequence[0]);
            user.setName(dto.getName());
            user.setEmail(dto.getEmail());
            user.setStatus(dto.getStatus());
            users.put(user.getId(), user);
            return Result.success(user);
        });
        org.mockito.Mockito.when(service.findPage(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            UserQueryDTO dto = call.getArgument(0);
            if (dto.getPageIndex() < 1 || dto.getPageSize() > 1000) {
                throw new BusinessException(BusinessException.Kind.VALIDATION, "分页参数无效");
            }
            return Result.success(PageResult.of(users.size(), List.copyOf(users.values())));
        });
        org.mockito.Mockito.when(service.findById(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            if (id < 1) throw new BusinessException(BusinessException.Kind.VALIDATION, "用户 ID 无效");
            UserVO user = users.get(id);
            if (user == null) throw new BusinessException(BusinessException.Kind.NOT_FOUND, "用户不存在");
            return Result.success(user);
        });
        org.mockito.Mockito.when(service.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(call -> {
                UserVO user = service.findById(call.getArgument(0)).getData();
                com.joykit.modular.user.dto.UserSaveDTO dto = call.getArgument(1);
                user.setName(dto.getName());
                return Result.success(user);
            });
        org.mockito.Mockito.when(service.delete(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            service.findById(id);
            users.remove(id);
            return Result.success(new UserDeleteVO(1));
        });
        org.mockito.Mockito.when(service.deleteBatch(org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            int count = 0;
            for (Long id : ids.stream().distinct().toList()) if (users.remove(id) != null) count++;
            return Result.success(new UserDeleteVO(count));
        });
        org.mockito.Mockito.when(service.deleteAll(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            if (!"删除全部用户".equals(call.getArgument(0))) {
                throw new BusinessException(BusinessException.Kind.VALIDATION, "确认文本错误");
            }
            int count = users.size();
            users.clear();
            return Result.success(new UserDeleteVO(count));
        });
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter()).build();
    }

    @Test
    void crudAndPagination() throws Exception {
        mvc.perform(post("/api/users").contentType("application/json").content(INPUT))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/users/1"))
            .andExpect(jsonPath("$.data.id").value("1"));
        mvc.perform(post("/api/users/page").contentType("application/json")
            .content("{\"pageIndex\":1,\"pageSize\":10,\"keyword\":\"测试\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].name").value("测试用户"));
        mvc.perform(get("/api/users/1")).andExpect(status().isOk());
        mvc.perform(put("/api/users/1").contentType("application/json").content(INPUT.replace("测试用户", "更新用户")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("更新用户"));
        mvc.perform(delete("/api/users/1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletedCount").value(1));
        mvc.perform(get("/api/users/1")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/1")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mvc.perform(post("/api/users/page").contentType("application/json").content("{\"pageIndex\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/page").contentType("application/json").content("{\"pageSize\":1001}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/page").contentType("application/json")
            .content("{\"keyword\":\"" + "a".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/-1")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").contentType("application/json").content("{\"name\":\" \",\"status\":2}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errors.name").isArray());
    }

    @Test
    void batchAndDeleteAllRequireValidInput() throws Exception {
        mvc.perform(post("/api/users").contentType("application/json").content(INPUT));
        mvc.perform(post("/api/users/batch-delete").contentType("application/json").content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/batch-delete").contentType("application/json").content("{\"ids\":[\"1\",\"1\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.deletedCount").value(1));
        mvc.perform(post("/api/users").contentType("application/json").content(INPUT));
        mvc.perform(post("/api/users/delete-all").contentType("application/json").content("{\"confirmation\":\"wrong\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/delete-all").contentType("application/json").content("{\"confirmation\":\"删除全部用户\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.deletedCount").value(1));
    }

}
