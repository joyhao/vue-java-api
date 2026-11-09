package com.joykit.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joykit.common.annotation.DevOnlyController;
import com.joykit.common.annotation.DevOnlyMethod;
import com.joykit.common.interceptor.DevAccessInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevAccessInterceptorTest {

    @Test
    void devProfileAllowsBothAnnotations() throws Exception {
        MockMvc mvc = buildMvc(environment("dev"));
        mvc.perform(get("/dev-method/only")).andExpect(status().isOk());
        mvc.perform(get("/dev-class/any")).andExpect(status().isOk());
    }

    @Test
    void prodProfileRejectsBothAnnotations() throws Exception {
        MockMvc mvc = buildMvc(environment("prod"));
        mvc.perform(get("/dev-method/only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该接口仅限开发环境访问"));
        mvc.perform(get("/dev-class/any"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void prodProfileAllowsUnannotatedEndpoint() throws Exception {
        MockMvc mvc = buildMvc(environment("prod"));
        mvc.perform(get("/dev-method/plain")).andExpect(status().isOk());
    }

    @Test
    void defaultProfileIsAllowedByDefault() throws Exception {
        MockMvc mvc = buildMvc(environment());
        mvc.perform(get("/dev-method/only")).andExpect(status().isOk());
    }

    @Test
    void defaultProfileCanBeDeniedByConfiguration() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.dev-access.allow-default-profile", "false");
        MockMvc mvc = buildMvc(environment);
        mvc.perform(get("/dev-method/only")).andExpect(status().isForbidden());
    }

    @Test
    void customDevProfilesAreSupported() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.dev-access.profiles", "local");
        environment.setActiveProfiles("local");
        MockMvc mvc = buildMvc(environment);
        mvc.perform(get("/dev-class/any")).andExpect(status().isOk());
    }

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        if (profiles.length > 0) {
            environment.setActiveProfiles(profiles);
        }
        return environment;
    }

    private static MockMvc buildMvc(MockEnvironment environment) {
        return MockMvcBuilders.standaloneSetup(new MethodLevelController(), new ClassLevelController())
                .addInterceptors(new DevAccessInterceptor(environment, new ObjectMapper()))
                .build();
    }

    @RestController
    @RequestMapping("/dev-method")
    public static class MethodLevelController {
        @GetMapping("/only")
        @DevOnlyMethod("仅开发环境开放的测试接口")
        public String only() {
            return "ok";
        }

        @GetMapping("/plain")
        public String plain() {
            return "ok";
        }
    }

    @RestController
    @RequestMapping("/dev-class")
    @DevOnlyController("整个控制器仅开发环境开放")
    public static class ClassLevelController {
        @GetMapping("/any")
        public String any() {
            return "ok";
        }
    }
}
