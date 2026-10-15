package dev.axonia.knowledge.adapter.out.health;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ArtifactSignalsAdapterTest extends CoreJdbcTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-04-15T18:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T18:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactSignalsAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new ArtifactSignalsAdapter(jdbc, () -> "default");
    }

    @Test
    void artifactCounts_perContributorInWindow() {

        // given
        artifact(7L, "alice", INSIDE);
        artifact(7L, "alice", INSIDE.plusSeconds(3600));
        artifact(7L, "bob", INSIDE);
        artifact(7L, "carol", BEFORE);
        artifact(8L, "dana", INSIDE); // other community

        // when
        var actual = sut.artifactCounts(7L, FROM, TO);

        // then
        assertThat(actual).containsOnly(entry("alice", 2L), entry("bob", 1L));
    }

    @Test
    void artifactsPerBucket_countsEachDayOnceAndOnlyThisCommunityInsideTheWindow() {

        // given a 3-day window: two artifacts on day 0, one on day 2, one before, one in another community
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(3));
        artifact(7L, "alice", from.plus(Duration.ofHours(1)));
        artifact(7L, "bob", from.plus(Duration.ofHours(23)));
        artifact(7L, "carol", from.plus(Duration.ofDays(2)).plus(Duration.ofHours(5)));
        artifact(7L, "dana", from.minus(Duration.ofHours(1)));
        artifact(8L, "erin", from.plus(Duration.ofHours(2)));

        // when
        var actual = sut.artifactsPerBucket(7L, from, to, 3);

        // then
        assertThat(actual).containsExactly(2L, 0L, 1L);
    }

    @Test
    void artifactsPerBucket_includesExactlyFromAndExcludesExactlyTo() {

        // given
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(3));
        artifact(7L, "alice", from);
        artifact(7L, "bob", to);
        artifact(7L, "carol", to.plusSeconds(1));

        // when
        var actual = sut.artifactsPerBucket(7L, from, to, 3);

        // then
        assertThat(actual).containsExactly(1L, 0L, 0L);
    }

    @Test
    void artifactsPerBucket_doesNotCountArchivedArtifacts() {

        // given
        var from = Instant.parse("2026-03-01T00:00:00Z");
        artifact(7L, "alice", from.plus(Duration.ofHours(1)), "ACTIVE");
        artifact(7L, "bob", from.plus(Duration.ofHours(2)), "ARCHIVED");

        // when
        var actual = sut.artifactsPerBucket(7L, from, from.plus(Duration.ofDays(3)), 3);

        // then
        assertThat(actual).containsExactly(1L, 0L, 0L);
    }

    @Test
    void artifactsPerBucket_ofNothingIsAllZeroes() {

        var from = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(sut.artifactsPerBucket(7L, from, from.plus(Duration.ofDays(90)), 90)).hasSize(90)
                .allMatch(count -> count == 0L);
    }

    @Test
    void artifactsPerBucket_answersTheWholeSeriesInOneQuery() {

        var template = mock(NamedParameterJdbcTemplate.class);
        var from = Instant.parse("2026-03-01T00:00:00Z");

        new ArtifactSignalsAdapter(template, () -> "default").artifactsPerBucket(7L, from,
                from.plus(Duration.ofDays(90)), 90);

        verify(template, times(1)).queryForList(anyString(), anyMap());
        verifyNoMoreInteractions(template);
    }

    private void artifact(long communityId, String contributorUserId, Instant createdAt) {

        artifact(communityId, contributorUserId, createdAt, "ACTIVE");
    }

    private void artifact(long communityId, String contributorUserId, Instant createdAt, String status) {

        jdbc.update("""
                INSERT INTO artifact (organization_id, community_id, type_id, title, description, url,
                        contributor_user_id, status, search_document, created_at, updated_at, version)
                VALUES ('default', :communityId, 1, 'T', 'd', 'https://example.com', :contributorUserId,
                        :status, 'doc', :createdAt, now(), 0)
                """,
                new MapSqlParameterSource().addValue("communityId", communityId)
                        .addValue("contributorUserId", contributorUserId).addValue("status", status)
                        .addValue("createdAt", Timestamp.from(createdAt)));
    }
}
