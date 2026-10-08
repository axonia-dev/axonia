package dev.axonia.events.internal;

import java.time.Instant;

import dev.axonia.events.SessionStatus;
import lombok.Builder;

/**
 * Window for retrospective session reads: non-cancelled sessions with {@code scheduledAt < now}. {@code from}
 * (inclusive) and {@code to} (exclusive) optionally narrow the window, and {@code status} - null for the whole window -
 * narrows it to one status, exactly as {@link SessionPageQuery} does for the page this counts.
 */
@Builder
public record PastSessionsQuery(String organizationId, Long communityId, Instant now, Instant from, Instant to,
        SessionStatus status) {
}
