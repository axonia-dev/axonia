package dev.axonia.events.internal;

import java.time.Instant;

import org.springframework.data.domain.Pageable;

import dev.axonia.events.SessionStatus;
import lombok.Builder;

/**
 * What one page of a community's sessions is: the tenant and community it belongs to, the window it covers, and how
 * much of it to read. {@link SessionListReader} is the only consumer.
 *
 * <p>
 * {@link Window} is an enum rather than a {@code boolean upcoming}, because there are three windows, not two:
 * {@link Window#BETWEEN} - the schedule's month view - keeps cancelled sessions, which the other two drop, and reads
 * its whole window rather than a page of it. A boolean cannot say that third thing, and a second boolean beside it
 * would let {@code upcoming && between} be written at all.
 *
 * <p>
 * {@code from} and {@code to} narrow every window the same way - {@code from} inclusive, {@code to} exclusive - so
 * {@link Window#BETWEEN}'s mandatory bounds and {@link Window#PAST}'s optional ones are one predicate rather than two.
 * {@code now} is the caller's clock, never the database's: {@link Window#PAST} and {@link Window#UPCOMING} are both
 * defined against it, and {@link Window#BETWEEN} needs none.
 *
 * <p>
 * {@code status} narrows any window to one status, and null - the usual case - leaves it as the window defines it. It
 * is what lets a caller ask for the occurrences that were actually held rather than for every one whose hour passed.
 */
@Builder
public record SessionPageQuery(String organizationId, Long communityId, Instant from, Instant to, Instant now,
        Window window, SessionStatus status, Pageable pageable) {

    /** Which sessions a page covers, and in what order they are read. */
    public enum Window {

        /** Non-cancelled sessions before {@code now}, newest first. */
        PAST,

        /** Non-cancelled sessions from {@code now} on, earliest first. */
        UPCOMING,

        /** Every session in {@code [from, to)} whatever its status, earliest first - the schedule's own window. */
        BETWEEN
    }
}
