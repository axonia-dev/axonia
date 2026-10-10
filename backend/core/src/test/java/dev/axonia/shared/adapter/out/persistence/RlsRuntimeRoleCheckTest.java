package dev.axonia.shared.adapter.out.persistence;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class RlsRuntimeRoleCheckTest {

    private final RlsRuntimeRoleCheck check = new RlsRuntimeRoleCheck(mock(javax.sql.DataSource.class));

    @Test
    void returnsTrueWhenRoleBypassesRls() {

        // given
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(true);

        // then
        assertThat(check.runtimeRoleBypassesRls(jdbc)).isTrue();
    }

    @Test
    void returnsFalseWhenRoleIsSubjectToRls() {

        // given
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(false);

        // then
        assertThat(check.runtimeRoleBypassesRls(jdbc)).isFalse();
    }

    @Test
    void returnsFalseWhenQueryReturnsNull() {

        // given
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(null);

        // then
        assertThat(check.runtimeRoleBypassesRls(jdbc)).isFalse();
    }

    @Test
    void aFailingCheckIsLoggedAndSkippedRatherThanThrown(CapturedOutput output) throws Exception {

        // given
        var failing = checkOver(jdbc -> when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
                .thenThrow(new DataAccessResourceFailureException("pg_roles unreachable")));

        // when
        failing.onReady();

        // then
        assertThat(output.getAll()).contains("could not verify runtime DB role against pg_roles");
    }

    @Test
    void anUnreadableCurrentUserIsReportedAsUnknown(CapturedOutput output) throws Exception {

        // given
        var unknown = checkOver(jdbc -> {
            when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(true);
            when(jdbc.queryForObject("SELECT current_user", String.class))
                    .thenThrow(new DataAccessResourceFailureException("no current_user"));
        });

        // when
        unknown.onReady();

        // then
        assertThat(output.getAll()).contains("runtime DB role '<unknown>' bypasses row-level security");
    }

    private static RlsRuntimeRoleCheck checkOver(Consumer<JdbcTemplate> stubbing) throws Exception {

        var jdbc = mock(JdbcTemplate.class);
        stubbing.accept(jdbc);
        var check = new RlsRuntimeRoleCheck(mock(javax.sql.DataSource.class));
        var field = RlsRuntimeRoleCheck.class.getDeclaredField("jdbc");
        field.setAccessible(true);
        field.set(check, jdbc);
        return check;
    }
}
