package com.joykit.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class LocalConfigImportTest {
    private static final Path LOCAL_CONFIG = Path.of("application-local.yml");

    @Test
    void localConfigStaysOffClasspath() {
        assertNull(
                getClass().getResource("/application-local.yml"),
                "application-local.yml 不能放在 src/main/resources 下，否则会被打进 jar");
    }

    @Test
    void applicationYmlImportsLocalConfig() {
        assumeTrue(
                Files.exists(LOCAL_CONFIG),
                "application-local.yml 已被 git 排除，仅本地存在时验证");
        try (ConfigurableApplicationContext context = minimalApplication().run()) {
            assertEquals("本地配置已加载", context.getEnvironment().getProperty("app.local.marker"));
        }
    }

    @Test
    void missingLocalConfigDoesNotBreakStartup() {
        try (ConfigurableApplicationContext context = minimalApplication()
                .run("--spring.config.import=optional:classpath:definitely-missing.yml")) {
            assertNotNull(context.getEnvironment().getProperty("spring.application.name"));
        }
    }

    @Test
    void dataSourcePasswordComesFromEnvironment() {
        assumeTrue(
                Files.exists(LOCAL_CONFIG),
                "application-local.yml 已被 git 排除，仅本地存在时验证");
        try (ConfigurableApplicationContext context = minimalApplication()
                .run("--DB_PASSWORD=from-env")) {
            assertEquals("from-env", context.getEnvironment().getProperty("spring.datasource.password"));
        }
    }

    @Test
    void prodProfileDisablesDevelopmentFeatures() {
        assumeTrue(
                getClass().getResource("/application-prod.yml") != null,
                "application-prod.yml 已被 git 排除，仅本地存在时验证");
        try (ConfigurableApplicationContext context = minimalApplication()
                .run("--spring.profiles.active=prod")) {
            assertEquals("false", context.getEnvironment().getProperty("springdoc.swagger-ui.enabled"));
            assertEquals("false", context.getEnvironment().getProperty("springdoc.api-docs.enabled"));
            assertEquals("false",
                    context.getEnvironment().getProperty("app.dev-access.allow-default-profile"));
        }
    }

    private static SpringApplicationBuilder minimalApplication() {
        return new SpringApplicationBuilder(MinimalConfig.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF);
    }

    @Configuration
    static class MinimalConfig {
    }
}
