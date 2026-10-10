package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import lombok.experimental.UtilityClass;

/**
 * The one place a cadence anchor and a series' preferred time become a real instant.
 *
 * <p>
 * A recurrence is a civil-time rule, not a moment: "18:00 in Zurich" converted to UTC once, at creation, freezes
 * whichever offset was in force that day, and the projected rhythm then slides an hour at each daylight-saving
 * transition. The zone is therefore an input to this function rather than a second time format - every instant that
 * leaves here, crosses the wire, or reaches the calendar feed is a plain UTC {@link Instant}.
 *
 * <p>
 * {@link ZonedDateTime#of} resolves a spring-forward gap forward and picks the earlier offset for an autumn overlap,
 * both silently, and that is deliberate. {@code Cadence} is documented as a hint rather than an authoritative
 * recurrence rule, so every resolution is still a valid "around then", and refusing the pick would fail a series
 * creation over an hour that occurs twice a year.
 */
@UtilityClass
public class OccurrenceInstant {

    /** A null {@code preferredTime} means midnight in the zone, matching the previous UTC-midnight behaviour. */
    public Instant at(LocalDate date, LocalTime preferredTime, ZoneId zone) {

        var time = preferredTime != null ? preferredTime : LocalTime.MIDNIGHT;
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    /** Which cadence slot an instant falls in, read in the series' own zone rather than in UTC. */
    public LocalDate anchorOf(Instant instant, ZoneId zone) {

        return instant.atZone(zone).toLocalDate();
    }
}
