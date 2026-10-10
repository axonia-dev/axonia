package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * A group synced from a customer's directory. {@code confirmedAt} is null while the group is held: a held group confers
 * none of the roles bound to its reconciliation key.
 */
@Builder(toBuilder = true)
public record DirectoryGroup(Long id, String organizationId, String scimId, String externalId, String displayName,
        String reconciliationKey, Instant confirmedAt, Instant createdAt, Instant updatedAt, Long version) {
}
