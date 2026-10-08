package dev.axonia.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
