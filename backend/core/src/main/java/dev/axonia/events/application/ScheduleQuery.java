package dev.axonia.events.application;

import java.time.Instant;

import lombok.Builder;

/**
 * One community's schedule window, named by ids rather than by a slug and carrying its own {@code now}. {@code now} is
 * what clamps ghost projection to today, so it is handed in rather than read from a clock: a caller that already knows
 * the instant it is answering for must get the same answer twice.
 *
 * <p>
 * {@link Builder} is the repo convention for a record at five components or more, this codebase's PMD ceiling for a
 * positional constructor.
 */
@Builder
public record ScheduleQuery(String organizationId, Long communityId, Instant from, Instant to, Instant now) {
}
