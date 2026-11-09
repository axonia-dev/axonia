package dev.axonia.knowledge.adapter.out.health;

import java.sql.Date;
import java.sql.Timestamp;
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
