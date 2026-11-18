package dev.axonia.community.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * One active community as the lobby ranks it for one caller. {@code nextSessionAt} is the start of the earliest session
 * not yet over, which may be under way already, or null when nothing is planned.
 */
@Builder
public record LobbyCandidate(long id, String name, long memberCount, boolean joined, long joinedLast30Days,
        Instant nextSessionAt) {
}
