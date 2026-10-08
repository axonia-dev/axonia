package dev.axonia.recognition.internal;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class RecognitionSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private RecognitionSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new RecognitionSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void lovesGiven_aggregateCountInWindow() {

        // given two in-window loves and one before the window in community 7,
        // plus an in-window love in community 8
        var rcA = recognizedContribution(7L, 100L);
        love(rcA, "bob", INSIDE);
        love(rcA, "carol", INSIDE.plusSeconds(60));
        love(rcA, "dana", BEFORE);
        love(recognizedContribution(8L, 200L), "erin", INSIDE);

        // when
        var actual = sut.lovesGiven(7L, FROM, TO);

        // then
        assertThat(actual).isEqualTo(2);
    }

    @Test
    void lovesPerBucket_placesEachLoveInItsOwnBucket() {

        // given a 12-day window bucketed by one day, with loves on day 0 and day 11
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(12));
        var rc = recognizedContribution(7L, 100L);
        love(rc, "bob", from.plus(Duration.ofHours(1)));
        love(rc, "carol", from.plus(Duration.ofHours(2)));
        love(rc, "dana", from.plus(Duration.ofDays(11)).plus(Duration.ofHours(3)));

        // when
        var actual = sut.lovesPerBucket(7L, from, to, 12);

        // then
        assertThat(actual).containsExactly(2L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    }

    @Test
    void lovesPerBucket_putsALoveAtTheLastTickOfTheWindowInTheFinalBucket() {

        // A love one millisecond before `to` - the closest a row inside the half-open window can sit
        // to its end - belongs in the last bucket, not off the end of the series.
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(12));
        love(recognizedContribution(7L, 100L), "bob", to.minusMillis(1));

        assertThat(sut.lovesPerBucket(7L, from, to, 12)).hasSize(12).element(11).isEqualTo(1L);
    }

    @Test
    void lovesPerBucket_ofAQuietCommunityIsTwelveZeroes() {

        var from = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(sut.lovesPerBucket(7L, from, from.plus(Duration.ofDays(12)), 12)).hasSize(12)
                .allMatch(count -> count == 0L);
    }

    @Test
    void lovesPerBucket_answersTheWholeSeriesInOneQueryRatherThanOnePerBucket() {

        // The series must be one grouped query. A loop over twelve sub-windows would pass every
        // behavioural test above and cost twelve round trips.
        var template = mock(NamedParameterJdbcTemplate.class);
        var from = Instant.parse("2026-03-01T00:00:00Z");

        new RecognitionSignalsAdapter(template, () -> "default").lovesPerBucket(7L, from,
                from.plus(Duration.ofDays(12)), 12);

        verify(template, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
        verifyNoMoreInteractions(template);
    }

    private long recognizedContribution(long communityId, long contributionId) {

        return jdbc.queryForObject("""
                INSERT INTO recognized_contribution (organization_id, contribution_type, contribution_id, community_id,
                        title, occurred_at, created_at, updated_at, version)
                VALUES ('default', 'SLOT_PRESENTED', :contributionId, :communityId, 'Talk',
                        now(), now(), now(), 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("communityId", communityId).addValue("contributionId",
                contributionId), Long.class);
    }

    private void love(long recognizedContributionId, String loverUserId, Instant createdAt) {

        jdbc.update("""
                INSERT INTO contribution_love (organization_id, recognized_contribution_id, lover_user_id,
                        created_at, updated_at, version)
                VALUES ('default', :rcId, :loverUserId, :createdAt, now(), 0)
                """, new MapSqlParameterSource().addValue("rcId", recognizedContributionId)
                .addValue("loverUserId", loverUserId).addValue("createdAt", Timestamp.from(createdAt)));
    }
}
