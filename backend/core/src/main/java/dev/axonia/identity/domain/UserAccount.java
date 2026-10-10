package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * One person's row in an organization's directory, column for column, {@code id} and {@code version} included so a save
 * round-trips exactly. What each column means is documented on the persistence entity that maps it.
 */
@Builder(toBuilder = true)
public record UserAccount(Long id, String organizationId, String userId, String displayName, String email,
        Boolean active, String externalId, String scimId, UserArrival arrival, String avatarMediaKey, String avatarEtag,
        Instant avatarSyncedAt, String avatarOverrideKey, Instant lastSeenAt, String observedTimeZone,
        String declaredTimeZone, String businessUnit, String department, String team, Instant createdAt,
        Instant updatedAt, Long version) {
}
