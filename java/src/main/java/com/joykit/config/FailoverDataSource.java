package com.joykit.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

public final class FailoverDataSource extends AbstractDataSource {
    private static final Logger LOG = LoggerFactory.getLogger(FailoverDataSource.class);
    private final DataSource primary;
    private final DataSource fallback;

    public FailoverDataSource(DataSource primary, DataSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return acquire(primary::getConnection, fallback::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return acquire(() -> primary.getConnection(username, password),
            () -> fallback.getConnection(username, password));
    }

    private Connection acquire(ConnectionSupplier first, ConnectionSupplier second) throws SQLException {
        try {
            return first.get();
        } catch (SQLException primaryFailure) {
            LOG.warn("blog 连接获取失败，尝试 postgres 备用库；SQLState={}", primaryFailure.getSQLState());
            try {
                return second.get();
            } catch (SQLException fallbackFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
                throw fallbackFailure;
            }
        }
    }

    @FunctionalInterface
    private interface ConnectionSupplier {
        Connection get() throws SQLException;
    }
}
