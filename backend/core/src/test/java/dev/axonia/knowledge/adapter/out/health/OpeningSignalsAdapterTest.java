package dev.axonia.knowledge.adapter.out.health;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactSeeds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class OpeningSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant ALSO_INSIDE = Instant.parse("2026-04-20T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private OpeningSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new OpeningSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void openingCounts_countsOpeningsAndDistinctArtifactsInWindow() {

        // given
        var one = ArtifactSeeds.artifact(jdbc, 7L, "alice", BEFORE);
        var two = ArtifactSeeds.artifact(jdbc, 7L, "alice", BEFORE);

        opening(7L, one, "bob", INSIDE);
        opening(7L, one, "carol", INSIDE);
        opening(7L, two, "bob", ALSO_INSIDE);

        // when
        var actual = sut.openingCounts(7L, FROM, TO);

        // then
        assertThat(actual.openings()).isEqualTo(3);
        assertThat(actual.distinctArtifactsOpened()).isEqualTo(2);
    }

    @Test
    void openingCounts_excludesTheContributorsOwnOpens() {

        // given
        var mine = ArtifactSeeds.artifact(jdbc, 7L, "alice", BEFORE);

        opening(7L, mine, "alice", INSIDE);
        opening(7L, mine, "bob", INSIDE);

        // when
        var actual = sut.openingCounts(7L, FROM, TO);

        // then
        assertThat(actual.openings()).isEqualTo(1);
        assertThat(actual.distinctArtifactsOpened()).isEqualTo(1);
    }

    @Test
    void openingCounts_ignoresOtherWindowsAndOtherCommunities() {

        // given
        var here = ArtifactSeeds.artifact(jdbc, 7L, "alice", BEFORE);
        var elsewhere = ArtifactSeeds.artifact(jdbc, 8L, "dana", BEFORE);

        opening(7L, here, "bob", BEFORE);
        opening(8L, elsewhere, "bob", INSIDE);

        // when
        var actual = sut.openingCounts(7L, FROM, TO);

        // then
        assertThat(actual.openings()).isZero();
        assertThat(actual.distinctArtifactsOpened()).isZero();
    }

    @Test
    void openingCounts_includesFromAndExcludesTo() {

        // given
        var artifactId = ArtifactSeeds.artifact(jdbc, 7L, "alice", BEFORE);

        opening(7L, artifactId, "bob", FROM);
        opening(7L, artifactId, "carol", TO);

        // when
        var actual = sut.openingCounts(7L, FROM, TO);

        // then
        assertThat(actual.openings()).isEqualTo(1);
    }

    @Test
    void openingCounts_countsOpeningsOfArchivedArtifacts() {

        // given
        var archived = ArtifactSeeds.artifact(jdbc, 7L, "alice", BEFORE, "ARCHIVED");

        opening(7L, archived, "bob", INSIDE);

        // when
        var actual = sut.openingCounts(7L, FROM, TO);

        // then
        assertThat(actual.openings()).isEqualTo(1);
        assertThat(actual.distinctArtifactsOpened()).isEqualTo(1);
    }

    @Test
    void openingsPerBucket_excludesTheContributorsOwnOpensLikeTheScalarReadDoes() {

        // given a 12-day window: a foreign open on day 0, a self-open on day 5, and a foreign open on
        // day 11 - the artifact itself was created on day 3, a bucket distinct from any of the opens,
        // so the series can only be right if it buckets by the opening's own created_at and not the
        // artifact's
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(12));
        var artifactId = ArtifactSeeds.artifact(jdbc, 7L, "alice", from.plus(Duration.ofDays(3)));
        opening(7L, artifactId, "bob", from.plus(Duration.ofHours(1)));
        opening(7L, artifactId, "alice", from.plus(Duration.ofDays(5)));
        opening(7L, artifactId, "carol", from.plus(Duration.ofDays(11)).plus(Duration.ofHours(3)));

        // when
        var actual = sut.openingsPerBucket(7L, from, to, 12);

        // then the self-open (bucket 5) is absent, exactly as it is absent from the scalar count, and
        // the two foreign opens land in the buckets their own created_at implies (0 and 11), not the
        // artifact's day-3 bucket
        assertThat(actual).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    }

    @Test
    void openingsPerBucket_ofNothingIsTwelveZeroes() {

        var from = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(sut.openingsPerBucket(7L, from, from.plus(Duration.ofDays(12)), 12)).hasSize(12)
                .allMatch(count -> count == 0L);
    }

    @Test
    void openingsPerBucket_answersTheWholeSeriesInOneQueryRatherThanOnePerBucket() {

        // The series must be one grouped query. A loop over twelve sub-windows would pass every
        // behavioural test above and cost twelve round trips.
        var template = mock(NamedParameterJdbcTemplate.class);
        var from = Instant.parse("2026-03-01T00:00:00Z");

        new OpeningSignalsAdapter(template, () -> "default").openingsPerBucket(7L, from, from.plus(Duration.ofDays(12)),
                12);

        verify(template, times(1)).queryForList(anyString(), anyMap());
        verifyNoMoreInteractions(template);
    }

    private void opening(long communityId, long artifactId, String memberUserId, Instant createdAt) {

        var sql = """
                INSERT INTO artifact_opening (organization_id, community_id, artifact_id, member_user_id,
                                              opened_on, created_at)
                VALUES ('default', :communityId, :artifactId, :memberUserId, :openedOn, :createdAt)
                """;
        jdbc.update(sql,
                new MapSqlParameterSource().addValue("communityId", communityId).addValue("artifactId", artifactId)
                        .addValue("memberUserId", memberUserId)
                        .addValue("openedOn", Date.valueOf(LocalDate.ofInstant(createdAt, ZoneOffset.UTC)))
                        .addValue("createdAt", Timestamp.from(createdAt)));
    }
}
