package dev.axonia.community.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import dev.axonia.community.api.LobbyShelfKey;

import static org.assertj.core.api.Assertions.assertThat;

class LobbyRankingTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void billboardRanksUnjoinedByGrowthThenThisWeekThenMembersThenName() {

        // given
        var grown = unjoined(1, "Grown").joinedLast30Days(9).build();
        var thisWeek = unjoined(2, "This Week").joinedLast30Days(3).nextSessionAt(NOW.plus(Duration.ofDays(2))).build();
        var crowded = unjoined(3, "Crowded").joinedLast30Days(3).memberCount(40).build();
        var alpha = unjoined(4, "alpha").joinedLast30Days(3).memberCount(10).build();
        var beta = unjoined(5, "Beta").joinedLast30Days(3).memberCount(10).build();
        var mine = joined(6, "Mine").joinedLast30Days(99).build();

        // when
        var actual = LobbyRanking.of(List.of(beta, mine, alpha, crowded, thisWeek, grown), NOW).billboard();

        // then
        assertThat(actual).containsExactly(grown, thisWeek, crowded, alpha, beta);
    }

    @Test
    void billboardHoldsFiveAtMost() {

        // given
        var candidates = IntStream.rangeClosed(1, 7).mapToObj(i -> unjoined(i, "C" + i).joinedLast30Days(i).build())
                .toList();

        // when
        var actual = LobbyRanking.of(candidates, NOW).billboard();

        // then
        assertThat(actual).extracting(LobbyCandidate::id).containsExactly(7L, 6L, 5L, 4L, 3L);
    }

    @Test
    void billboardBreaksAFullTieById() {

        // given
        var second = unjoined(2, "Same").build();
        var first = unjoined(1, "Same").build();

        // when
        var actual = LobbyRanking.of(List.of(second, first), NOW).billboard();

        // then
        assertThat(actual).containsExactly(first, second);
    }

    @Test
    void allJoined() {

        // given
        var quiet = joined(1, "Quiet").build();
        var busy = joined(2, "Busy").joinedLast30Days(5).build();

        // when
        var actual = LobbyRanking.of(List.of(quiet, busy), NOW);

        // then
        assertThat(actual.billboard()).containsExactly(busy, quiet);
        assertThat(actual.shelves()).containsOnlyKeys(LobbyShelfKey.JOINED);
        assertThat(actual.shelves().get(LobbyShelfKey.JOINED)).containsExactly(busy, quiet);
    }

    @Test
    void noCommunityAtAll() {

        // when
        var actual = LobbyRanking.of(List.of(), NOW);

        // then
        assertThat(actual.billboard()).isEmpty();
        assertThat(actual.shelves()).isEmpty();
    }

    @Test
    void shelvesAssignByPrecedence() {

        // given
        var soonest = unjoined(1, "Zulu").joinedLast30Days(4).nextSessionAt(NOW.plus(Duration.ofDays(3))).build();
        var later = unjoined(2, "Alpha").nextSessionAt(NOW.plus(Duration.ofDays(5))).build();
        var growingFew = unjoined(3, "Bravo").joinedLast30Days(2).build();
        var growingMany = unjoined(4, "Yankee").joinedLast30Days(6).build();
        var growingTie = unjoined(5, "Charlie").joinedLast30Days(2).build();
        var farOff = unjoined(6, "Delta").nextSessionAt(NOW.plus(Duration.ofDays(30))).build();
        var still = unjoined(7, "echo").build();
        var mineZ = joined(8, "Zebra").build();
        var mineA = joined(9, "apple").build();

        // when
        var actual = LobbyRanking
                .of(List.of(soonest, later, growingFew, growingMany, growingTie, farOff, still, mineZ, mineA), NOW)
                .shelves();

        // then
        assertThat(actual.keySet()).containsExactly(LobbyShelfKey.JOINED, LobbyShelfKey.THIS_WEEK,
                LobbyShelfKey.GROWING, LobbyShelfKey.OTHERS);
        assertThat(actual.get(LobbyShelfKey.JOINED)).containsExactly(mineA, mineZ);
        assertThat(actual.get(LobbyShelfKey.THIS_WEEK)).containsExactly(soonest, later);
        assertThat(actual.get(LobbyShelfKey.GROWING)).containsExactly(growingMany, growingFew, growingTie);
        assertThat(actual.get(LobbyShelfKey.OTHERS)).containsExactly(farOff, still);
    }

    @Test
    void thisWeekBreaksATieOnTheSessionByName() {

        // given
        var at = NOW.plus(Duration.ofDays(1));
        var bravo = unjoined(1, "Bravo").nextSessionAt(at).build();
        var alpha = unjoined(2, "Alpha").nextSessionAt(at).build();

        // when
        var actual = LobbyRanking.of(withFiller(bravo, alpha), NOW).shelves().get(LobbyShelfKey.THIS_WEEK);

        // then
        assertThat(actual).containsExactly(alpha, bravo);
    }

    @Test
    void sessionAtExactlySevenDaysIsNotThisWeek() {

        // given
        var candidate = unjoined(1, "Edge").nextSessionAt(NOW.plus(Duration.ofDays(7))).build();

        // when
        var actual = LobbyRanking.of(List.of(candidate), NOW);

        // then
        assertThat(actual.thisWeek(candidate)).isFalse();
    }

    @Test
    void sessionAtNowIsThisWeek() {

        // given
        var candidate = unjoined(1, "Edge").nextSessionAt(NOW).build();

        // when
        var actual = LobbyRanking.of(List.of(candidate), NOW);

        // then
        assertThat(actual.thisWeek(candidate)).isTrue();
    }

    @Test
    void nextSessionAlreadyUnderWayIsThisWeek() {

        // given: a next session is one not yet over, so one that started before now is in progress
        var candidate = unjoined(1, "Edge").nextSessionAt(NOW.minus(Duration.ofMinutes(20))).build();

        // when
        var actual = LobbyRanking.of(List.of(candidate), NOW);

        // then
        assertThat(actual.thisWeek(candidate)).isTrue();
    }

    @Test
    void noSessionIsNotThisWeek() {

        // given
        var candidate = unjoined(1, "Edge").build();

        // when
        var actual = LobbyRanking.of(List.of(candidate), NOW);

        // then
        assertThat(actual.thisWeek(candidate)).isFalse();
    }

    @Test
    void collapsesAtSix() {

        // given
        var mine = joined(1, "Mine").build();
        var growing = unjoined(2, "Zulu").joinedLast30Days(3).build();
        var soon = unjoined(3, "Alpha").nextSessionAt(NOW.plus(Duration.ofDays(1))).build();
        var rest = List.of(unjoined(4, "Delta").build(), unjoined(5, "bravo").build(), unjoined(6, "Charlie").build());
        var six = new ArrayList<>(List.of(mine, growing, soon));
        six.addAll(rest);

        // when
        var actual = LobbyRanking.of(six, NOW).shelves();

        // then
        assertThat(actual.keySet()).containsExactly(LobbyShelfKey.JOINED, LobbyShelfKey.EXPLORE);
        assertThat(actual.get(LobbyShelfKey.EXPLORE)).extracting(LobbyCandidate::name).containsExactly("Alpha", "bravo",
                "Charlie", "Delta", "Zulu");
    }

    @Test
    void doesNotCollapseAtSeven() {

        // given
        var seven = new ArrayList<LobbyCandidate>();
        seven.add(joined(1, "Mine").build());
        seven.add(unjoined(2, "Soon").nextSessionAt(NOW.plus(Duration.ofDays(1))).build());
        seven.add(unjoined(3, "Growing").joinedLast30Days(1).build());
        IntStream.rangeClosed(4, 7).forEach(i -> seven.add(unjoined(i, "Rest " + i).build()));

        // when
        var actual = LobbyRanking.of(seven, NOW).shelves();

        // then
        assertThat(actual.keySet()).containsExactly(LobbyShelfKey.JOINED, LobbyShelfKey.THIS_WEEK,
                LobbyShelfKey.GROWING, LobbyShelfKey.OTHERS);
    }

    @Test
    void emptyShelvesAreOmitted() {

        // given
        var seven = IntStream.rangeClosed(1, 7).mapToObj(i -> unjoined(i, "Rest " + i).build()).toList();

        // when
        var actual = LobbyRanking.of(seven, NOW).shelves();

        // then
        assertThat(actual.keySet()).containsExactly(LobbyShelfKey.OTHERS);
    }

    @Test
    void collapsedLobbyWithEveryCommunityJoinedHasNoExplore() {

        // when
        var actual = LobbyRanking.of(List.of(joined(1, "Mine").build()), NOW).shelves();

        // then
        assertThat(actual.keySet()).containsExactly(LobbyShelfKey.JOINED);
    }

    @Test
    void byNameKeepsTheGivenCommunitiesInNameOrder() {

        // given
        var mine = joined(1, "Guild Alpha").build();
        var theirs = unjoined(2, "backend guild").build();
        var other = unjoined(3, "Circle").build();

        // when
        var actual = LobbyRanking.of(List.of(mine, theirs, other), NOW).byName(Set.of(1L, 2L, 99L));

        // then
        assertThat(actual).containsExactly(theirs, mine);
    }

    private static List<LobbyCandidate> withFiller(LobbyCandidate... candidates) {

        var all = new ArrayList<>(List.of(candidates));
        IntStream.rangeClosed(100, 106).forEach(i -> all.add(unjoined(i, "Filler " + i).build()));
        return all;
    }

    private static LobbyCandidate.LobbyCandidateBuilder unjoined(long id, String name) {

        return LobbyCandidate.builder().id(id).name(name).memberCount(1).joined(false);
    }

    private static LobbyCandidate.LobbyCandidateBuilder joined(long id, String name) {

        return unjoined(id, name).joined(true);
    }
}
