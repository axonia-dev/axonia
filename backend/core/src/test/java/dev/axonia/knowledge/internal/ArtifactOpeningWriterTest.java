package dev.axonia.knowledge.internal;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactOpeningWriterTest extends CoreJdbcTestSupport {

    private static final Instant MORNING = Instant.parse("2026-04-15T08:00:00Z");
    private static final Instant AFTERNOON = Instant.parse("2026-04-15T16:00:00Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-04-16T08:00:00Z");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ArtifactOpeningWriter sut;

    @BeforeEach
    void setUp() {

        sut = new ArtifactOpeningWriter(jdbc);
    }

    @Test
    void record_collapsesRepeatsWithinOneDay() {

        // given
        var artifactId = ArtifactSeeds.artifact(jdbc, 7L, "alice", MORNING);

        // when
        sut.record(opening(artifactId, "bob", MORNING, LocalDate.of(2026, 4, 15)));
        sut.record(opening(artifactId, "bob", AFTERNOON, LocalDate.of(2026, 4, 15)));

        // then
        assertThat(openings(artifactId, "bob")).isEqualTo(1);
        var row = openingRow(artifactId, "bob");
        assertThat(row.createdAt()).isEqualTo(MORNING);
        assertThat(row.openedOn()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(row.communityId()).isEqualTo(7L);
        assertThat(row.organizationId()).isEqualTo("default");
    }

    @Test
    void record_countsTheNextDayAgain() {

        // given
        var artifactId = ArtifactSeeds.artifact(jdbc, 7L, "alice", MORNING);

        // when
        sut.record(opening(artifactId, "bob", MORNING, LocalDate.of(2026, 4, 15)));
        sut.record(opening(artifactId, "bob", NEXT_DAY, LocalDate.of(2026, 4, 16)));

        // then
        assertThat(openings(artifactId, "bob")).isEqualTo(2);
    }

    @Test
    void record_keepsTwoMembersApartOnTheSameDay() {

        // given
        var artifactId = ArtifactSeeds.artifact(jdbc, 7L, "alice", MORNING);

        // when
        sut.record(opening(artifactId, "bob", MORNING, LocalDate.of(2026, 4, 15)));
        sut.record(opening(artifactId, "carol", MORNING, LocalDate.of(2026, 4, 15)));

        // then
        assertThat(openings(artifactId, "bob")).isEqualTo(1);
        assertThat(openings(artifactId, "carol")).isEqualTo(1);
    }

    private RecordOpeningCommand opening(long artifactId, String memberUserId, Instant createdAt, LocalDate openedOn) {

        return RecordOpeningCommand.builder().organizationId("default").communityId(7L).artifactId(artifactId)
                .memberUserId(memberUserId).createdAt(createdAt).openedOn(openedOn).build();
    }

    private int openings(long artifactId, String memberUserId) {

        var sql = "SELECT count(*) FROM artifact_opening WHERE artifact_id = :artifactId "
                + "AND member_user_id = :memberUserId";
        return jdbc.queryForObject(sql,
                new MapSqlParameterSource().addValue("artifactId", artifactId).addValue("memberUserId", memberUserId),
                Integer.class);
    }

    private OpeningRow openingRow(long artifactId, String memberUserId) {

        var sql = "SELECT organization_id, community_id, created_at, opened_on FROM artifact_opening "
                + "WHERE artifact_id = :artifactId AND member_user_id = :memberUserId";
        return jdbc.queryForObject(sql,
                new MapSqlParameterSource().addValue("artifactId", artifactId).addValue("memberUserId", memberUserId),
                (rs, n) -> new OpeningRow(rs.getString("organization_id"), rs.getLong("community_id"),
                        rs.getTimestamp("created_at").toInstant(), rs.getDate("opened_on").toLocalDate()));
    }

    private record OpeningRow(String organizationId, long communityId, Instant createdAt, LocalDate openedOn) {
    }
}
