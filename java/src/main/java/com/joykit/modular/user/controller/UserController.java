package com.joykit.modular.user.controller;

import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import com.joykit.common.constants.ApiConstants;
import com.joykit.modular.user.docs.UserApiDocs;
import com.joykit.modular.user.dto.UserIdsDTO;
import com.joykit.modular.user.dto.UserClearDTO;
import com.joykit.modular.user.po.Employee;
import com.joykit.modular.user.vo.UserDeleteVO;
import com.joykit.modular.user.vo.UserVO;
import com.joykit.modular.user.dto.UserSaveDTO;
import com.joykit.modular.user.dto.UserQueryDTO;
import com.joykit.modular.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理", description = "用户资料维护、分页查询与删除管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.USER_PATH)
public class UserController {
    private final UserService userService;

    @Operation(summary = UserApiDocs.LIST_SUMMARY, description = UserApiDocs.LIST_DESCRIPTION)
    @PostMapping("/page")
    public Result<PageResult<UserVO>> list(@Valid @RequestBody UserQueryDTO dto) {
        return userService.findPage(dto);
    }


    /**
     * 测试数组自身的方法，以及 Arrays 工具类提供的数组操作。
     * 返回字符串数组，由 Spring MVC 序列化为 JSON 数组，供前端控制台查看。
     */
    @PostMapping("/test")
    public Object test() {
        Employee[] employees = {
                new Employee("张三", "开发部", 28, 8500.0),
                new Employee("李四", "测试部", 24, 6000.0),
                new Employee("王五", "开发部", 32, 12000.0),
                new Employee("赵六", "运营部", 29, 7000.0),
                new Employee("钱七", "开发部", 25, 9000.0),
                new Employee("孙八", "测试部", 35, 11000.0)
        };
//      List<String> names =   Arrays.stream(employees).filter(item-> {
//            return  item.department().startsWith("开发部");
//        }).map(item->item.name()).collect(Collectors.toList());

     //   List<Employee> p =   Arrays.stream(employees).sorted(Comparator.comparingDouble(Employee::salary)).limit(2).toList();

        Map<String, List<Employee>> p =  Arrays.stream(employees).collect(Collectors.groupingBy(item -> item.department().substring(0, item.department().length() )));

        return p;
    }


    @Operation(summary = UserApiDocs.GET_SUMMARY, description = UserApiDocs.GET_DESCRIPTION)
    @GetMapping("/{id}")
    public Result<UserVO> get(@Parameter(description = "用户 ID，必须为正整数", example = "1")
                              @PathVariable long id) {
        return userService.findById(id);
    }

    @Operation(summary = UserApiDocs.CREATE_SUMMARY, description = UserApiDocs.CREATE_DESCRIPTION)
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserSaveDTO input) {
        return userService.create(input);
    }

    @Operation(summary = UserApiDocs.UPDATE_SUMMARY, description = UserApiDocs.UPDATE_DESCRIPTION)
    @PutMapping("/{id}")
    public Result<UserVO> update(
            @Parameter(description = "用户 ID，必须为正整数", example = "1") @PathVariable long id,
            @Valid @RequestBody UserSaveDTO input) {
        return userService.update(id, input);
    }

    @Operation(summary = UserApiDocs.DELETE_SUMMARY, description = UserApiDocs.DELETE_DESCRIPTION)
    @DeleteMapping("/{id}")
    public Result<UserDeleteVO> delete(@Parameter(description = "用户 ID，必须为正整数", example = "1")
                                       @PathVariable long id) {
        return userService.delete(id);
    }

    @Operation(summary = UserApiDocs.BATCH_DELETE_SUMMARY, description = UserApiDocs.BATCH_DELETE_DESCRIPTION)
    @PostMapping("/batch-delete")
    public Result<UserDeleteVO> deleteBatch(@Valid @RequestBody UserIdsDTO input) {
        return userService.deleteBatch(input.ids());
    }

    @Operation(summary = UserApiDocs.DELETE_ALL_SUMMARY, description = UserApiDocs.DELETE_ALL_DESCRIPTION)
    @PostMapping("/delete-all")
    public Result<UserDeleteVO> deleteAll(@Valid @RequestBody UserClearDTO input) {
        return userService.deleteAll(input.confirmation());
    }
}
