package dev.axonia.identity.internal;

import java.time.Instant;

import lombok.Builder;

/** Column values written by one successful avatar sync — a parameter object for the targeted repository update. */
@Builder
public record AvatarSyncUpdate(String organizationId, String userId, String avatarMediaKey, String avatarEtag,
        Instant avatarSyncedAt) {
}
