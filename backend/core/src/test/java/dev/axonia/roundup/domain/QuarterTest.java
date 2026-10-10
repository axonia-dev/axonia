package dev.axonia.roundup.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quarter arithmetic is tested directly rather than through the producer: an off-by-one here - a boundary instant
 * landing in the wrong quarter, a January tick looking back into the wrong year - would otherwise only ever show up as
 * a roundup nobody could explain, three months after the tick that got it wrong.
 */
class QuarterTest {

    @Test
    void theMostRecentlyClosedQuarterOnTheFirstDayOfTheNextOne() {

        var quarter = Quarter.mostRecentlyClosed(Instant.parse("2026-07-01T00:00:00Z"));

        assertThat(quarter.key()).isEqualTo("2026-Q2");
        assertThat(quarter.start()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
        assertThat(quarter.endExclusive()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    void theLastInstantOfAQuarterStillBelongsToTheQuarterBeforeIt() {

        assertThat(Quarter.mostRecentlyClosed(Instant.parse("2026-06-30T23:59:59Z")).key()).isEqualTo("2026-Q1");
    }

    @Test
    void januaryLooksBackIntoThePreviousYear() {

        var quarter = Quarter.mostRecentlyClosed(Instant.parse("2026-01-15T00:00:00Z"));

        assertThat(quarter.key()).isEqualTo("2025-Q4");
        assertThat(quarter.start()).isEqualTo(Instant.parse("2025-10-01T00:00:00Z"));
        assertThat(quarter.endExclusive()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** The quarter the roundup looks ahead into. Q4 is the one that has to roll the year rather than reach a fifth. */
    @Test
    void theFollowingQuarterRollsIntoTheNextYearAfterTheFourth() {

        assertThat(new Quarter(2026, 2).next()).isEqualTo(new Quarter(2026, 3));
        assertThat(new Quarter(2026, 4).next()).isEqualTo(new Quarter(2027, 1));
        assertThat(new Quarter(2026, 4).next().endExclusive()).isEqualTo(Instant.parse("2027-04-01T00:00:00Z"));
    }

    /**
     * The window is half-open, so the last instant of a quarter is outside it - the same {@code [from, to)} the
     * activity ports are declared over. Asserted on the fourth quarter specifically: it is the one whose end crosses a
     * year boundary, where a naive {@code +3 months} on the month number alone would overflow rather than roll over.
     */
    @Test
    void theWindowEndsWhereTheNextQuarterBegins() {

        var quarter = Quarter.mostRecentlyClosed(Instant.parse("2026-02-01T00:00:00Z"));

        assertThat(quarter.start()).isBefore(quarter.endExclusive());
        assertThat(quarter.endExclusive()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
