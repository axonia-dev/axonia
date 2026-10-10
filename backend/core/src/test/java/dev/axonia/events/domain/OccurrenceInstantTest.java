package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OccurrenceInstantTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Test
    void resolvesSummerTimeAtTheZonesOwnOffset() {

        var at = OccurrenceInstant.at(LocalDate.of(2026, 8, 18), LocalTime.of(18, 0), ZURICH);

        assertThat(at).isEqualTo(Instant.parse("2026-08-18T16:00:00Z"));
    }

    @Test
    void resolvesWinterTimeAnHourLaterInUtcSoTheLocalTimeHolds() {

        var at = OccurrenceInstant.at(LocalDate.of(2026, 11, 17), LocalTime.of(18, 0), ZURICH);

        assertThat(at).isEqualTo(Instant.parse("2026-11-17T17:00:00Z"));
    }

    @Test
    void treatsAMissingPreferredTimeAsMidnightInTheZone() {

        var at = OccurrenceInstant.at(LocalDate.of(2026, 8, 18), null, ZURICH);

        assertThat(at).isEqualTo(Instant.parse("2026-08-17T22:00:00Z"));
    }

    @Test
    void resolvesAWallClockTimeThatNeverOccursRatherThanRefusingIt() {

        var at = OccurrenceInstant.at(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), ZURICH);

        assertThat(at).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
    }

    @Test
    void anchorOfReadsTheDateBackInTheSameZone() {

        var anchor = OccurrenceInstant.anchorOf(Instant.parse("2026-08-17T22:00:00Z"), ZURICH);

        assertThat(anchor).isEqualTo(LocalDate.of(2026, 8, 18));
    }
}
