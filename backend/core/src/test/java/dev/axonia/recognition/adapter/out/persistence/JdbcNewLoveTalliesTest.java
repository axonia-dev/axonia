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
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.NewLoveQuery;
import dev.axonia.recognition.domain.NewLoveTally;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcNewLoveTallies.class)
class JdbcNewLoveTalliesTest extends CoreJdbcTestSupport {

    private static final String ME = "alice";
    private static final Instant AS_OF = Instant.parse("2026-10-17T12:00:00.123Z");
    private static final Instant FLOOR = AS_OF.minus(30, ChronoUnit.DAYS);

    @Autowired
    private RecognizedContributionRepository contributions;

    @Autowired
    private LoveRepository loves;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private JdbcNewLoveTallies sut;

    private static Instant daysAgo(long days) {

        return AS_OF.minus(days, ChronoUnit.DAYS);
    }

    private Long recognize(Long contributionId, Long communityId) {

        return contributions
                .save(RecognizedContributionEntityMother.any().contributionType(ContributionType.SLOT_PRESENTED)
                        .contributionId(contributionId).communityId(communityId).build())
                .getId();
    }

    private Long love(Long recognizedContributionId, String loverUserId, Instant createdAt) {

        var id = loves.save(LoveEntityMother.any().recognizedContributionId(recognizedContributionId)
                .loverUserId(loverUserId).build()).getId();
        jdbc.update("UPDATE contribution_love SET created_at = :at WHERE id = :id",
                Map.of("at", Timestamp.from(createdAt), "id", id));
        return id;
    }

    private void markSeen(Long communityId, Instant seenAt) {

        jdbc.update(
                "INSERT INTO impact_visit (organization_id, user_id, community_id, seen_at) "
                        + "VALUES ('default', :user, :community, :seenAt)",
                Map.of("user", ME, "community", communityId, "seenAt", Timestamp.from(seenAt)));
    }

    private List<NewLoveTally> since(List<Long> contributionIds) {

        return sut.since(NewLoveQuery.builder().organizationId("default").userId(ME)
                .contributionType(ContributionType.SLOT_PRESENTED).contributionIds(contributionIds).floor(FLOOR)
                .asOf(AS_OF).build());
    }

    private static NewLoveTally tally(Long contributionId, long newLoves, boolean capped) {

        return NewLoveTally.builder().contributionType(ContributionType.SLOT_PRESENTED).contributionId(contributionId)
                .newLoves(newLoves).capped(capped).build();
    }

    @Test
    void countsLovesAfterTheMarkerUpToAsOfAndIsNotCappedByAFreshMarker() {

        // given
        var slot = recognize(7L, 1L);
        markSeen(1L, daysAgo(5));
        love(slot, "u1", daysAgo(6));
        love(slot, "u2", daysAgo(5));
        love(slot, "u3", daysAgo(4));
        love(slot, "u4", AS_OF);

        // when
        var actual = since(List.of(7L));

        // then
        assertThat(actual).containsExactly(tally(7L, 2, false));
    }

    @Test
    void leavesOutTheMembersOwnLoveAndLovesAfterAsOf() {

        // given
        var slot = recognize(7L, 1L);
        markSeen(1L, daysAgo(5));
        love(slot, ME, daysAgo(1));
        love(slot, "u1", daysAgo(1));
        love(slot, "u2", AS_OF.plusMillis(1));

        // when
        var actual = since(List.of(7L));

        // then
        assertThat(actual).containsExactly(tally(7L, 1, false));
    }

    @Test
    void withoutAMarkerCountsFromTheFloorAndReportsCapped() {

        // given
        var counted = recognize(7L, 1L);
        var beforeFloorOnly = recognize(8L, 1L);
        love(counted, "u1", FLOOR.minusMillis(1));
        love(counted, "u2", FLOOR.plusMillis(1));
        love(beforeFloorOnly, "u1", FLOOR.minus(1, ChronoUnit.DAYS));

        // when
        var actual = since(List.of(7L, 8L));

        // then
        assertThat(actual).containsExactly(tally(7L, 1, true));
    }

    @Test
    void aMarkerOlderThanTheFloorCountsFromTheFloorAndReportsCapped() {

        // given
        var slot = recognize(7L, 1L);
        markSeen(1L, FLOOR.minus(10, ChronoUnit.DAYS));
        love(slot, "u1", FLOOR.minus(5, ChronoUnit.DAYS));
        love(slot, "u2", daysAgo(1));

        // when
        var actual = since(List.of(7L));

        // then
        assertThat(actual).containsExactly(tally(7L, 1, true));
    }

    @Test
    void eachCommunityUsesItsOwnMarker() {

        // given
        var inOne = recognize(7L, 1L);
        var inTwo = recognize(8L, 2L);
        markSeen(1L, daysAgo(2));
        markSeen(2L, daysAgo(10));
        love(inOne, "u1", daysAgo(5));
        love(inOne, "u2", daysAgo(1));
        love(inTwo, "u1", daysAgo(5));
        love(inTwo, "u2", daysAgo(1));

        // when
        var actual = since(List.of(7L, 8L));

        // then
        assertThat(actual).containsExactlyInAnyOrder(tally(7L, 1, false), tally(8L, 2, false));
    }

    @Test
    void aLoveTakenBackAndGivenAgainAfterTheMarkerCountsOnce() {

        // given
        var slot = recognize(7L, 1L);
        markSeen(1L, daysAgo(5));
        var first = love(slot, "u1", daysAgo(10));
        loves.deleteById(first);
        love(slot, "u1", daysAgo(1));

        // when
        var actual = since(List.of(7L));

        // then
        assertThat(actual).containsExactly(tally(7L, 1, false));
    }

    @Test
    void contributionsWithNoNewLoveAreAbsent() {

        // given
        var loved = recognize(7L, 1L);
        var stale = recognize(8L, 1L);
        recognize(9L, 1L);
        markSeen(1L, daysAgo(5));
        love(loved, "u1", daysAgo(1));
        love(stale, "u1", daysAgo(6));

        // when
        var actual = since(List.of(7L, 8L, 9L, 10L));

        // then
        assertThat(actual).containsExactly(tally(7L, 1, false));
    }

    @Test
    void anotherOrganizationsMarkersAndLovesNeverReachTheTally() {

        // given
        var slot = recognize(7L, 1L);
        love(slot, "u1", daysAgo(3));
        var otherSlot = contributions
                .save(RecognizedContributionEntityMother.any().organizationId("other")
                        .contributionType(ContributionType.SLOT_PRESENTED).contributionId(7L).communityId(1L).build())
                .getId();
        var otherLove = loves.save(LoveEntityMother.any().organizationId("other").recognizedContributionId(otherSlot)
                .loverUserId("u2").build()).getId();
        jdbc.update("UPDATE contribution_love SET created_at = :at WHERE id = :id",
                Map.of("at", Timestamp.from(daysAgo(1)), "id", otherLove));
        jdbc.update("INSERT INTO impact_visit (organization_id, user_id, community_id, seen_at) "
                + "VALUES ('other', :user, 1, :seenAt)", Map.of("user", ME, "seenAt", Timestamp.from(AS_OF)));

        // when
        var actual = since(List.of(7L));

        // then
        assertThat(actual).containsExactly(tally(7L, 1, true));
    }

    @Test
    void noContributionsAnswersNothing() {

        assertThat(since(List.of())).isEmpty();
    }
}
