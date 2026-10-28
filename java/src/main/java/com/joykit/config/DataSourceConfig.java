package com.joykit.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties blogDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource blogDataSource(
            @Qualifier("blogDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource pool = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        pool.setInitializationFailTimeout(-1);
        return pool;
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource postgresDataSource(
            @Qualifier("blogDataSourceProperties") DataSourceProperties properties,
            @Value("${app.datasource.postgres.url}") String url) {
        HikariDataSource pool = properties.initializeDataSourceBuilder().type(HikariDataSource.class)
            .url(url).build();
        pool.setInitializationFailTimeout(-1);
        return pool;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("blogDataSource") DataSource primary,
                                 @Qualifier("postgresDataSource") DataSource fallback) {
        return new FailoverDataSource(primary, fallback);
    }

    @Bean
    public JdbcTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
