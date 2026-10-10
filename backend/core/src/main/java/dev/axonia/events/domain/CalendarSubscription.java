package dev.axonia.events.domain;

import java.time.Instant;

/**
 * A member's calendar subscription to one community, as the dialog reads it.
 *
 * <p>
 * {@code url} is non-null only immediately after a mint or a reset. The token it carries is never stored, so it cannot
 * be shown a second time: reading an existing subscription returns the fetch stamp and no URL, and obtaining a fresh
 * URL means resetting the old one.
 *
 * <p>
 * {@code lastFetchedAt} and {@code lastFetchedAgent} are how an otherwise opaque subscription proves itself. A URL
 * pasted into the wrong box never syncs, and nothing else anywhere would say so.
 */
public record CalendarSubscription(String url, Instant lastFetchedAt, String lastFetchedAgent) {
}
