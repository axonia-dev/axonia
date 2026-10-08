package dev.axonia.identity.internal;

import lombok.Builder;

/**
 * Profile values written by the JIT user upsert - one row per (organization, subject). Avatars are not among them: see
 * {@link AvatarSyncService}.
 */
@Builder
public record UserUpsert(String organizationId, String userId, String displayName, String email, String scimId) {
}
