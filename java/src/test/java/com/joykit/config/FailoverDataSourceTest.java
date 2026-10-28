package com.joykit.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FailoverDataSourceTest {
    @Test
    void prefersBlogWithoutCallingPostgres() throws SQLException {
        DataSource blog = mock(DataSource.class);
        DataSource postgres = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(blog.getConnection()).thenReturn(connection);
        assertThat(new FailoverDataSource(blog, postgres).getConnection()).isSameAs(connection);
        verifyNoInteractions(postgres);
    }

    @Test
    void fallsBackAndPrefersBlogAgainOnNextAcquisition() throws SQLException {
        DataSource blog = mock(DataSource.class);
        DataSource postgres = mock(DataSource.class);
        Connection backupConnection = mock(Connection.class);
        Connection restoredConnection = mock(Connection.class);
        when(blog.getConnection()).thenThrow(new SQLException("unavailable", "08001"))
            .thenReturn(restoredConnection);
        when(postgres.getConnection()).thenReturn(backupConnection);
        var source = new FailoverDataSource(blog, postgres);
        assertThat(source.getConnection()).isSameAs(backupConnection);
        assertThat(source.getConnection()).isSameAs(restoredConnection);
        verify(postgres, times(1)).getConnection();
    }

    @Test
    void preservesBothFailuresWhenNeitherDatabaseIsAvailable() throws SQLException {
        DataSource blog = mock(DataSource.class);
        DataSource postgres = mock(DataSource.class);
        SQLException first = new SQLException("primary");
        SQLException second = new SQLException("backup");
        when(blog.getConnection()).thenThrow(first);
        when(postgres.getConnection()).thenThrow(second);
        assertThatThrownBy(() -> new FailoverDataSource(blog, postgres).getConnection())
            .isSameAs(second).hasSuppressedException(first);
    }
}
