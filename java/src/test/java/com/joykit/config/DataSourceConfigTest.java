package com.joykit.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.support.JdbcTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigTest {
    @Test
    void usesFailoverAsPrimaryAndSharesOneTransactionEntry() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(DataSourceConfig.class)
            .withPropertyValues(
                "spring.datasource.url=jdbc:postgresql://localhost:9006/blog",
                "spring.datasource.username=postgres",
                "spring.datasource.password=test-only",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.datasource.hikari.maximum-pool-size=20",
                "spring.datasource.hikari.minimum-idle=4",
                "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'Asia/Shanghai'",
                "app.datasource.postgres.url=jdbc:postgresql://localhost:9006/postgres")
            .run(context -> {
                assertThat(context).hasNotFailed();
                HikariDataSource blog = context.getBean("blogDataSource", HikariDataSource.class);
                DataSource routing = context.getBean("dataSource", DataSource.class);
                HikariDataSource postgres = context.getBean("postgresDataSource", HikariDataSource.class);
                assertThat(context.getBean(DataSource.class)).isSameAs(routing);
                assertThat(routing).isInstanceOf(FailoverDataSource.class);
                assertThat(postgres).isNotSameAs(blog);
                assertThat(blog.getJdbcUrl()).endsWith("/blog");
                assertThat(postgres.getJdbcUrl()).endsWith("/postgres");
                assertThat(postgres.getUsername()).isEqualTo(blog.getUsername());
                assertThat(postgres.getPassword()).isEqualTo(blog.getPassword());
                assertThat(postgres.getMaximumPoolSize()).isEqualTo(20);
                assertThat(postgres.getMinimumIdle()).isEqualTo(4);
                assertThat(postgres.getConnectionInitSql()).isEqualTo(blog.getConnectionInitSql());
                assertThat(context.getBean(JdbcTransactionManager.class).getDataSource()).isSameAs(routing);
                assertThat(context).doesNotHaveBean("postgresTransactionManager");
            });
    }
}
