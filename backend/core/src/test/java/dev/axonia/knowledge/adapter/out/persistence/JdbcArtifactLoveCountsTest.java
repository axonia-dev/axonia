package dev.axonia.knowledge.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@MockitoSettings
class JdbcArtifactLoveCountsTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void increment_addsOneWithinTheCommunity() {

        // when
        new JdbcArtifactLoveCounts(jdbc).increment(7L, 1L);

        // then
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(
                eq("UPDATE artifact SET love_count = love_count + 1 WHERE id = :id AND community_id = :communityId"),
                params.capture());
        assertThat(params.getValue().getValues()).containsEntry("id", 7L).containsEntry("communityId", 1L);
    }

    @Test
    void decrement_subtractsOneButNeverBelowZero() {

        // when
        new JdbcArtifactLoveCounts(jdbc).decrement(7L, 1L);

        // then
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(eq("UPDATE artifact SET love_count = GREATEST(love_count - 1, 0)"
                + " WHERE id = :id AND community_id = :communityId"), params.capture());
        assertThat(params.getValue().getValues()).containsEntry("id", 7L).containsEntry("communityId", 1L);
    }
}
