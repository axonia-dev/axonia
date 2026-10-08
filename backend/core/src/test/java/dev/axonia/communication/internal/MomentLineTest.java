package dev.axonia.communication.internal;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thursday 1 October 2026 16:00Z is 09:00 in Los Angeles (PDT, UTC-7), 18:00 in Berlin and Paris (CEST, UTC+2), 21:30
 * in Kolkata, and already Friday in Auckland. Every expectation below was checked against a real conversion rather than
 * worked out by hand.
 */
class MomentLineTest {

    private static final Instant AT = Instant.parse("2026-10-01T16:00:00Z");

    @Test
    void rendersOnTheReadersOwnClockAndNamesTheSessionsAlongside() {

        var line = MomentLine.full(facts("America/Los_Angeles", "Europe/Berlin"));

        assertThat(line).isEqualTo("Thursday, 1 October 2026, 18:00 in Berlin (09:00 in Los Angeles)");
    }

    /** The defect this whole thread exists for: a Berlin member was told 16:00 for a session starting at 18:00. */
    @Test
    void neverStatesUtcToAReaderWhoLivesSomewhereElse() {

        var line = MomentLine.full(facts("America/Los_Angeles", "Europe/Berlin"));

        assertThat(line).doesNotContain("UTC").doesNotContain("16:00");
    }

    @Test
    void aReaderOnTheSessionsOwnClockIsToldItOnce() {

        var line = MomentLine.full(facts("Europe/Berlin", "Europe/Berlin"));

        assertThat(line).isEqualTo("Thursday, 1 October 2026, 18:00 in Berlin");
    }

    /**
     * Compared by offset rather than by zone id. Paris and Berlin share a wall clock, and "18:00 in Paris (18:00 in
     * Berlin)" states one fact twice.
     */
    @Test
    void twoZonesSharingAWallClockGetNoParenthetical() {

        var line = MomentLine.full(facts("Europe/Berlin", "Europe/Paris"));

        assertThat(line).isEqualTo("Thursday, 1 October 2026, 18:00 in Paris");
    }

    @Test
    void aReaderWhoseDayIsAlreadyTomorrowIsToldTheirOwnDay() {

        var line = MomentLine.full(facts("America/Los_Angeles", "Pacific/Auckland"));

        assertThat(line).isEqualTo("Friday, 2 October 2026, 05:00 in Auckland (09:00 in Los Angeles)");
    }

    @Test
    void anUnknownReaderClockFallsBackToTheSessionsOwnZoneRatherThanUtc() {

        var line = MomentLine.full(facts("America/Los_Angeles", null));

        assertThat(line).isEqualTo("Thursday, 1 October 2026, 09:00 in Los Angeles");
    }

    /**
     * An untimed occurrence stores midnight in its series' zone, so the day it falls on is the session's answer and
     * never the reader's: rendered on a reader's clock west of the session, it would name the day before the one on the
     * schedule.
     */
    @Test
    void anUntimedOccurrenceNamesItsDayOnTheSessionsClock() {

        var facts = facts("Europe/Paris", "America/Los_Angeles");
        facts.put("scheduledAt", Instant.parse("2026-08-12T22:00:00Z"));
        facts.put("hasTime", false);

        assertThat(MomentLine.full(facts)).isEqualTo("Thursday, 13 August 2026");
    }

    /** A fact map written before hasTime existed keeps the timed line it has always had. */
    @Test
    void aFactMapWithNoHasTimeKeepsTheTimedLine() {

        var facts = new HashMap<String, Object>();
        facts.put("scheduledAt", AT);
        facts.put("timeZone", "Europe/Berlin");

        assertThat(MomentLine.full(facts)).isEqualTo("Thursday, 1 October 2026, 18:00 in Berlin");
    }

    @Test
    void nothingToRenderWhenTheFactsCarryNoInstant() {

        assertThat(MomentLine.full(Map.of("timeZone", "Europe/Berlin"))).isNull();
        assertThat(MomentLine.compact(Map.of("timeZone", "Europe/Berlin"))).isNull();
    }

    /** A session whose row predates the zone column renders in UTC, which is the behaviour it already had. */
    @Test
    void aSessionWithNoZoneAtAllStillRenders() {

        var facts = new HashMap<String, Object>();
        facts.put("scheduledAt", AT);
        facts.put("hasTime", true);

        assertThat(MomentLine.full(facts)).isEqualTo("Thursday, 1 October 2026, 16:00 in UTC");
    }

    /** The shape a chat card and the in-app panel have always had, now on the session's clock instead of UTC. */
    @Test
    void theCompactFormKeepsItsOwnShape() {

        assertThat(MomentLine.compact(facts("Europe/Berlin", null))).isEqualTo("1 October 2026 at 18:00 in Berlin");
    }

    private static Map<String, Object> facts(String sessionZone, String readerZone) {

        var facts = new HashMap<String, Object>();
        facts.put("scheduledAt", AT);
        facts.put("hasTime", true);
        facts.put("timeZone", sessionZone);
        facts.put(MomentLine.READER_ZONE_FACT, readerZone);
        return facts;
    }
}
