package dev.axonia.community.application;

import java.time.Instant;

import dev.axonia.communication.api.ChatPlatformName;

/**
 * One of a community's chat destinations as anyone outside this module may see it. The stored URL never appears: it is
 * a bearer token for a room, so what leaves is the platform it belongs to and enough of the URL for a leader to
 * recognise which one they pasted. A null {@code platform} means the stored URL belongs to no platform this deploy's
 * allowlist claims, which an older, laxer deploy could have written.
 */
public record ChatDestination(long id, String name, ChatPlatformName platform, String maskedUrl, boolean broken,
        Instant lastPostedAt, Instant lastFailureAt, String lastError) {
}
