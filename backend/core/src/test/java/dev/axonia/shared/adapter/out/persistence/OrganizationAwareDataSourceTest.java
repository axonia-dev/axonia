package dev.axonia.shared.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

class OrganizationAwareDataSourceTest {

    @Test
    void setsAndResetsTenantGucWhenPresent() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        var stmt = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(raw);
        when(raw.prepareStatement("SELECT set_config('app.current_org', ?, false)")).thenReturn(ps);
        when(raw.createStatement()).thenReturn(stmt);

        // when
        var ds = new OrganizationAwareDataSource(delegate, () -> Optional.of("acme"), Optional::empty);
        var conn = ds.getConnection();

        // then
        verify(ps).setString(1, "acme");
        verify(ps).execute();

        conn.close();
        verify(stmt).execute("RESET app.current_org");
        verify(raw).close();
    }

    @Test
    void skipsSetWhenNoTenant() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var stmt = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(raw);
        when(raw.createStatement()).thenReturn(stmt);

        // when
        var ds = new OrganizationAwareDataSource(delegate, Optional::empty, Optional::empty);
        var conn = ds.getConnection();

        // then
        verify(stmt, never()).execute(startsWith("SET app.current_org"));

        conn.close();
        verify(stmt, never()).execute(startsWith("RESET"));
        verify(raw).close();
    }

    @Test
    void setsAndResetsBothGucsInOneStatementWhenBothPresent() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        var stmt = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(raw);
        when(raw.prepareStatement(
                "SELECT set_config('app.current_org', ?, false), set_config('app.current_subject', ?, false)"))
                .thenReturn(ps);
        when(raw.createStatement()).thenReturn(stmt);

        // when
        var ds = new OrganizationAwareDataSource(delegate, () -> Optional.of("acme"),
                () -> Optional.of("subject-alice"));
        var conn = ds.getConnection();

        // then
        verify(ps).setString(1, "acme");
        verify(ps).setString(2, "subject-alice");
        verify(ps).execute();

        conn.close();
        verify(stmt).execute("RESET app.current_org; RESET app.current_subject");
        verify(raw).close();
    }

    @Test
    void setsAndResetsSubjectGucWhenPresent() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        var stmt = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(raw);
        when(raw.prepareStatement("SELECT set_config('app.current_subject', ?, false)")).thenReturn(ps);
        when(raw.createStatement()).thenReturn(stmt);

        // when
        var ds = new OrganizationAwareDataSource(delegate, Optional::empty, () -> Optional.of("subject-alice"));
        var conn = ds.getConnection();

        // then
        verify(ps).setString(1, "subject-alice");
        verify(ps).execute();

        conn.close();
        verify(stmt).execute("RESET app.current_subject");
        verify(raw).close();
    }

    @Test
    void skipsSetWhenNoSubject() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var stmt = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(raw);
        when(raw.createStatement()).thenReturn(stmt);

        // when
        var ds = new OrganizationAwareDataSource(delegate, Optional::empty, Optional::empty);
        var conn = ds.getConnection();

        // then
        verify(stmt, never()).execute(startsWith("SET app.current_subject"));

        conn.close();
        verify(stmt, never()).execute(startsWith("RESET"));
        verify(raw).close();
    }

    @Test
    void credentialsAreForwardedToTheDelegateAndTheConnectionIsStillScoped() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        when(delegate.getConnection("alice", "secret")).thenReturn(raw);
        when(raw.prepareStatement("SELECT set_config('app.current_org', ?, false)")).thenReturn(ps);

        // when
        var ds = new OrganizationAwareDataSource(delegate, () -> Optional.of("acme"), Optional::empty);
        ds.getConnection("alice", "secret");

        // then
        verify(ps).setString(1, "acme");
        verify(ps).execute();
    }

    @Test
    void anExceptionFromADelegatedMethodSurfacesAsItself() throws Exception {

        // given
        var delegate = mock(DataSource.class);
        var raw = mock(Connection.class);
        var failure = new SQLException("connection is broken");
        when(delegate.getConnection()).thenReturn(raw);
        when(raw.isValid(1)).thenThrow(failure);
        var conn = new OrganizationAwareDataSource(delegate, Optional::empty, Optional::empty).getConnection();

        // when
        var actual = catchThrowable(() -> conn.isValid(1));

        // then
        assertThat(actual).isSameAs(failure);
    }
}
