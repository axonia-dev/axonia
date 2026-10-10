package dev.axonia.identity.domain;

import lombok.Builder;

/**
 * Profile values written by the JIT user upsert - one row per (organization, subject). Avatars are not among them: see
 * {@code AvatarSyncService}.
 */
@Builder
public record UserUpsert(String organizationId, String userId, String displayName, String email, String scimId) {
}
