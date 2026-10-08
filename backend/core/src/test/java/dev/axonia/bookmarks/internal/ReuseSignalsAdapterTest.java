package dev.axonia.bookmarks.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.health.ReuseCounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ReuseSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ReuseSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new ReuseSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void reuseCounts_aggregatesDemandAndPayoffExcludingSelfSavesAndSessions() {

        // given artifacts authored by alice; two artifacts reused by others, a helped/not-helped mix
        bookmark("ARTIFACT", 100L, "bob", "alice", true, INSIDE); // helped = true
        bookmark("ARTIFACT", 100L, "carol", "alice", null, INSIDE); // unanswered
        bookmark("ARTIFACT", 101L, "erin", "alice", false, INSIDE); // helped = false
        bookmark("ARTIFACT", 100L, "alice", "alice", null, INSIDE); // self-save — excluded
        bookmark("ARTIFACT", 100L, "dana", "alice", false, BEFORE); // out of window — excluded
        bookmark("SESSION", 200L, "frank", "alice", null, INSIDE); // not an artifact — excluded

        // when
        var actual = sut.reuseCounts(7L, FROM, TO);

        // then bob + carol + erin counted across artifacts {100, 101}; one helped, one not helped
        assertThat(actual).isEqualTo(new ReuseCounts(3L, 2L, 1L, 1L));
    }

    @Test
    void savesPerBucket_excludesSelfSavesLikeTheScalarReadDoes() {

        // given a 12-day window: a self-save at hour 1, a foreign save at hour 2, and a foreign save on day 11
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(12));
        bookmark("ARTIFACT", 100L, "alice", "alice", null, from.plus(Duration.ofHours(1))); // self-save - excluded
        bookmark("ARTIFACT", 100L, "bob", "alice", null, from.plus(Duration.ofHours(2)));
        bookmark("ARTIFACT", 101L, "carol", "alice", null, from.plus(Duration.ofDays(11)));

        // when
        var actual = sut.savesPerBucket(7L, from, to, 12);

        // then the self-save is absent, exactly as it is absent from the scalar count
        assertThat(actual).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    }

    @Test
    void savesPerBucket_ofNothingIsTwelveZeroes() {

        var from = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(sut.savesPerBucket(7L, from, from.plus(Duration.ofDays(12)), 12)).hasSize(12)
                .allMatch(count -> count == 0L);
    }

    @Test
    void savesPerBucket_answersTheWholeSeriesInOneQueryRatherThanOnePerBucket() {

        // The series must be one grouped query. A loop over twelve sub-windows would pass every
        // behavioural test above and cost twelve round trips.
        var template = mock(NamedParameterJdbcTemplate.class);
        var from = Instant.parse("2026-03-01T00:00:00Z");

        new ReuseSignalsAdapter(template, () -> "default").savesPerBucket(7L, from, from.plus(Duration.ofDays(12)), 12);

        verify(template, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
        verifyNoMoreInteractions(template);
    }

    private void bookmark(String saveableType, long saveableId, String memberUserId, String sourceAuthorUserId,
            Boolean helped, Instant createdAt) {

        jdbc.update("""
                INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id,
                        source_author_user_id, helped, helped_at, created_at, updated_at, version)
                VALUES ('default', 7, :saveableType, :saveableId, :memberUserId, :sourceAuthorUserId, :helped,
                        CASE WHEN CAST(:helped AS boolean) IS NULL THEN NULL ELSE now() END, :createdAt, now(), 0)
                """,
                new MapSqlParameterSource().addValue("saveableType", saveableType).addValue("saveableId", saveableId)
                        .addValue("memberUserId", memberUserId).addValue("sourceAuthorUserId", sourceAuthorUserId)
                        .addValue("helped", helped).addValue("createdAt", Timestamp.from(createdAt)));
    }
}
