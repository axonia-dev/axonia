package dev.axonia.identity.domain;

import lombok.Builder;

/**
 * Profile values written by the JIT user upsert - one row per (organization, subject). {@code receivedName} is the name
 * as asserted, {@code displayName} and {@code givenName} what the Organization's name patterns read out of it. Avatars
 * are not among them: see {@code AvatarSyncService}.
 */
@Builder
public record UserUpsert(String organizationId, String userId, String displayName, String receivedName,
        String givenName, String email, String scimId) {
}
