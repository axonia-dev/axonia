package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import dev.axonia.events.Cadence;
import dev.axonia.events.CadenceUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NextSlotTest {

    // A Wednesday.
    private static final LocalDate ANCHOR = LocalDate.parse("2026-06-03");
    private static final Instant NOW = Instant.parse("2026-06-08T00:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static NextSlotQuery query(Cadence cadence, LocalTime preferredTime, Instant now) {

        return NextSlotQuery.builder().cadence(cadence).startsOn(ANCHOR).preferredTime(preferredTime).zone(UTC).now(now)
                .build();
    }

    @Test
    void weeklyReturnsFirstOccurrenceAtOrAfterNow() {

        // when
        // Anchor 06-03 (past); weekly steps 06-03, 06-10 — first >= 06-08 is 06-10.
        var actual = NextSlot.suggest(query(new Cadence(1, CadenceUnit.WEEK), LocalTime.of(18, 0), NOW));

        // then
        assertThat(actual).contains(Instant.parse("2026-06-10T18:00:00Z"));
    }

    @Test
    void biweeklyStepsFourteenDaysFromAnchor() {

        // when
        // Anchor 06-03; biweekly steps 06-03, 06-17 — first >= 06-08 is 06-17.
        var actual = NextSlot.suggest(query(new Cadence(2, CadenceUnit.WEEK), LocalTime.of(9, 0), NOW));

        // then
        assertThat(actual).contains(Instant.parse("2026-06-17T09:00:00Z"));
    }

    @Test
    void monthlyStepsOneMonthFromAnchor() {

        // when
        // Anchor 06-03; monthly steps 06-03, 07-03 — first >= 06-08 is 07-03.
        var actual = NextSlot.suggest(query(new Cadence(1, CadenceUnit.MONTH), LocalTime.of(12, 0), NOW));

        // then
        assertThat(actual).contains(Instant.parse("2026-07-03T12:00:00Z"));
    }

    @Test
    void futureAnchorReturnsTheAnchorItself() {

        // given
        // Anchor 06-03 but now is before it: first occurrence is the anchor.
        var earlierNow = Instant.parse("2026-06-01T00:00:00Z");

        // when
        var actual = NextSlot.suggest(query(new Cadence(1, CadenceUnit.WEEK), LocalTime.of(10, 0), earlierNow));

        // then
        assertThat(actual).contains(Instant.parse("2026-06-03T10:00:00Z"));
    }

    @Test
    void nullTimeDefaultsToMidnightUtc() {

        // when
        var actual = NextSlot.suggest(query(new Cadence(1, CadenceUnit.WEEK), null, NOW));

        // then
        assertThat(actual).contains(Instant.parse("2026-06-10T00:00:00Z"));
    }

    @Test
    void returnsEmptyWhenNowIsBeyondTheStepHorizon() {

        // given
        // 1200 weekly steps from the 2026 anchor reach ~2049; a far-future now is never met.
        var farFuture = Instant.parse("2400-01-01T00:00:00Z");

        // then
        assertThat(NextSlot.suggest(query(new Cadence(1, CadenceUnit.WEEK), null, farFuture))).isEmpty();
    }

    @Test
    void occurrencesBetween_weekly_yieldsEachAnchorInWindow() {

        // given
        var from = LocalDate.parse("2026-06-13");
        var to = LocalDate.parse("2026-07-04"); // exclusive

        // when
        var result = NextSlot.occurrencesBetween(new Cadence(1, CadenceUnit.WEEK), LocalDate.parse("2026-06-05"), from,
                to);

        // then
        assertThat(result).containsExactly(LocalDate.parse("2026-06-19"), LocalDate.parse("2026-06-26"),
                LocalDate.parse("2026-07-03"));
    }

    @Test
    void occurrencesBetween_skipsDatesBeforeFrom() {

        // when
        var result = NextSlot.occurrencesBetween(new Cadence(1, CadenceUnit.MONTH), LocalDate.parse("2026-01-10"),
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-08-01"));

        // then
        assertThat(result).containsExactly(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-07-10"));
    }

    @Test
    void occurrencesBetween_everyNineDays() {
        var result = NextSlot.occurrencesBetween(new Cadence(9, CadenceUnit.DAY), LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"));
        assertThat(result).containsExactly(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"),
                LocalDate.parse("2026-06-19"), LocalDate.parse("2026-06-28"));
    }

    @Test
    void occurrencesBetween_everyTwoYears() {
        var result = NextSlot.occurrencesBetween(new Cadence(2, CadenceUnit.YEAR), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2031-01-01"));
        assertThat(result).containsExactly(LocalDate.parse("2026-01-01"), LocalDate.parse("2028-01-01"),
                LocalDate.parse("2030-01-01"));
    }
}
