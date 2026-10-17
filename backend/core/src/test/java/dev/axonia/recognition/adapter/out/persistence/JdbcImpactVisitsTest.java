package dev.axonia.recognition.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcImpactVisits.class)
class JdbcImpactVisitsTest extends CoreJdbcTestSupport {

    private static final Instant NOON = Instant.parse("2026-10-17T12:00:00.123456Z").truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private JdbcImpactVisits sut;

    private Instant seenAt(String organizationId, String userId, long communityId) {

        return jdbc
                .queryForObject(
                        "SELECT seen_at FROM impact_visit WHERE organization_id = :org AND user_id = :user "
                                + "AND community_id = :community",
                        Map.of("org", organizationId, "user", userId, "community", communityId), Timestamp.class)
                .toInstant();
    }

    private long rows() {

        return jdbc.queryForObject("SELECT count(*) FROM impact_visit", Map.of(), Long.class);
    }

    @Test
    void insertsOneRowPerCommunityAtTheGivenInstant() {

        // when
        sut.markSeen("default", "alice", List.of(1L, 2L), NOON);

        // then
        assertThat(rows()).isEqualTo(2);
        assertThat(seenAt("default", "alice", 1L)).isEqualTo(NOON);
        assertThat(seenAt("default", "alice", 2L)).isEqualTo(NOON);
    }

    @Test
    void anEarlierVisitLeavesTheMarkerWhereItIs() {

        // given
        sut.markSeen("default", "alice", List.of(1L), NOON);

        // when
        sut.markSeen("default", "alice", List.of(1L), NOON.minus(1, ChronoUnit.HOURS));

        // then
        assertThat(seenAt("default", "alice", 1L)).isEqualTo(NOON);
    }

    @Test
    void aLaterVisitMovesTheMarker() {

        // given
        sut.markSeen("default", "alice", List.of(1L), NOON);
        var later = NOON.plus(1, ChronoUnit.HOURS);

        // when
        sut.markSeen("default", "alice", List.of(1L), later);

        // then
        assertThat(seenAt("default", "alice", 1L)).isEqualTo(later);
    }

    @Test
    void leavesOtherUsersAndOtherOrganizationsUntouched() {

        // given
        sut.markSeen("default", "bob", List.of(1L), NOON);
        sut.markSeen("other", "alice", List.of(1L), NOON);
        var later = NOON.plus(1, ChronoUnit.HOURS);

        // when
        sut.markSeen("default", "alice", List.of(1L), later);

        // then
        assertThat(rows()).isEqualTo(3);
        assertThat(seenAt("default", "bob", 1L)).isEqualTo(NOON);
        assertThat(seenAt("other", "alice", 1L)).isEqualTo(NOON);
        assertThat(seenAt("default", "alice", 1L)).isEqualTo(later);
    }

    @Test
    void noCommunitiesWritesNothing() {

        // when
        sut.markSeen("default", "alice", List.of(), NOON);

        // then
        assertThat(rows()).isZero();
    }
}
