package dev.axonia.events;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import dev.axonia.ValidTimeZone;

/**
 * When a session meets, as the caller states it: a civil date, an optional wall-clock time, and the zone that time is
 * read in.
 *
 * <p>
 * Not an {@code Instant}, deliberately. An instant cannot express "this occurrence has no hour", and a client composing
 * one itself is a client deciding what a typed time means - which is how an organizer working abroad used to move a
 * community's session by an unnoticed offset. The composition belongs to
 * {@link dev.axonia.events.internal.OccurrenceInstant}, the one implementation of a rule this codebase has already got
 * wrong once, and to the server that knows which zone actually governs.
 *
 * <p>
 * {@code zone} is authoritative only for a session with no series. An occurrence of a series composes in its series'
 * zone and this value is ignored, so no client can reschedule a community's session into a zone the community did not
 * choose.
 */
public record ScheduledAt(@NotNull LocalDate date, LocalTime time, @NotBlank @ValidTimeZone String zone) {
}
