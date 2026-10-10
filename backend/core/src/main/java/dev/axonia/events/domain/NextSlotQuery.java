package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import lombok.Builder;

/**
 * The question {@link NextSlot#suggest} answers: stepping a {@code cadence} from {@code startsOn} at
 * {@code preferredTime} in {@code zone}, which occurrence is the first at or after {@code now}. {@link Builder} is the
 * repo convention for a record at five components or more, this codebase's PMD ceiling for a positional constructor.
 */
@Builder
public record NextSlotQuery(Cadence cadence, LocalDate startsOn, LocalTime preferredTime, ZoneId zone, Instant now) {
}
