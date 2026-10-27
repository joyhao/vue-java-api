package com.joykit.common;


import com.joykit.common.exception.GlobalExceptionHandler;
import com.joykit.common.filter.TraceIdFilter;
import com.joykit.common.result.Result;

import com.joykit.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiContractTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ContractController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter()).build();
    }

    @Test
    void explicitSuccessPreservesCreatedStatus() throws Exception {
        mvc.perform(get("/test/success")).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.name").value("joykit"))
            .andExpect(jsonPath("$.timestamp").isNumber());
        mvc.perform(post("/test/create")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void explicitResultsSupportStringData() throws Exception {
        mvc.perform(get("/test/wrapped")).andExpect(jsonPath("$.data").value("value"));
        mvc.perform(get("/test/string")).andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void validationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/test/validate").contentType("application/json").content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data.errors.name").isArray());
    }

    @Test
    void businessErrorRetainsHttpStatus() throws Exception {
        mvc.perform(get("/test/conflict")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409)).andExpect(jsonPath("$.message").value("数据冲突"));
    }

    @Test
    void databaseErrorDoesNotLeakDetails() throws Exception {
        mvc.perform(get("/test/database")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void malformedJsonAndUnsupportedMethodAreUnified() throws Exception {
        mvc.perform(post("/test/validate").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(delete("/test/success")).andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value(405));
    }

    @RestController
    static class ContractController {
        @GetMapping("/test/success") Result<Map<String, String>> success() { return Result.success(Map.of("name", "joykit")); }
        @PostMapping("/test/create") ResponseEntity<Result<Map<String, String>>> create() {
            return ResponseEntity.status(201).body(Result.success(Map.of("id", "1")));
        }
        @GetMapping("/test/string") Result<String> text() { return Result.success("hello"); }
        @GetMapping("/test/wrapped") Result<String> wrapped() { return Result.success("value"); }
        @PostMapping("/test/validate") Input validate(@Valid @RequestBody Input input) { return input; }
        @GetMapping("/test/conflict") void conflict() {
            throw new BusinessException(BusinessException.Kind.CONFLICT, "数据冲突");
        }
        @GetMapping("/test/database") void database() {
            throw new DataAccessResourceFailureException("secret SQL connection details");
        }
    }

    record Input(@NotBlank String name) { }
}
