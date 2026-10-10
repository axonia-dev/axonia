package dev.axonia.communication.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * One row of a member's notification panel, rendered from its message's frozen facts at the moment it is read rather
 * than stored anywhere. Nothing here is persisted: a second copy of a message's content is exactly what this module has
 * always refused, and rendering on read is also what makes the ninety-day expiry coherent, since an expired entry has
 * no facts left to render and leaves no orphaned text behind.
 *
 * <p>
 * {@code sender} is null when the community has no name of its own, the same absent state the mail shell and the chat
 * post both use rather than naming a stand-in.
 */
@Builder
public record NotificationEntry(long id, String sender, String headline, String line, String actionUrl, Instant sentAt,
        boolean read) {
}
