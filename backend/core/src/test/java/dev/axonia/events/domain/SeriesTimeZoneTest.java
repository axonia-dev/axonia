package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesTimeZoneTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    @Test
    void aFortnightlySeriesHoldsItsLocalTimeAcrossTheAutumnTransition() {

        var cadence = new Cadence(2, CadenceUnit.WEEK);
        var startsOn = LocalDate.of(2026, 8, 18);

        var summer = NextSlot.suggest(NextSlotQuery.builder().cadence(cadence).startsOn(startsOn)
                .preferredTime(LocalTime.of(18, 0)).zone(ZURICH).now(Instant.parse("2026-08-01T00:00:00Z")).build());
        var winter = NextSlot.suggest(NextSlotQuery.builder().cadence(cadence).startsOn(startsOn)
                .preferredTime(LocalTime.of(18, 0)).zone(ZURICH).now(Instant.parse("2026-11-16T00:00:00Z")).build());

        assertThat(summer).contains(Instant.parse("2026-08-18T16:00:00Z"));
        // The fortnightly sequence from 18 August lands on 24 November, after the autumn
        // transition: 18, Sep 1, Sep 15, Sep 29, Oct 13, Oct 27, Nov 10, Nov 24.
        assertThat(winter).contains(Instant.parse("2026-11-24T17:00:00Z"));
        assertThat(OccurrenceInstant.anchorOf(winter.orElseThrow(), ZURICH)).isEqualTo(LocalDate.of(2026, 11, 24));
    }
}
