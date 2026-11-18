package dev.axonia.community.api;

import java.time.Instant;

/**
 * A session as the lobby names it. {@code title} is null when the session has no name of its own. {@code hasTime} is
 * false when nobody chose an hour, and {@code scheduledAt} is then midnight of its date in {@code zone}. {@code zone}
 * is the IANA id of the clock that governs the session, never null.
 */
public record LobbySession(String title, Instant scheduledAt, boolean hasTime, String zone) {
}
