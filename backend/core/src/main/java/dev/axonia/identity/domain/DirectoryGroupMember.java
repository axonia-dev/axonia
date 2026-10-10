package dev.axonia.identity.domain;

import java.time.Instant;

import lombok.Builder;

/** One synced membership of a user, by SCIM id, in a directory group. */
@Builder(toBuilder = true)
public record DirectoryGroupMember(Long id, String organizationId, Long groupId, String userScimId, Instant createdAt,
        Instant updatedAt, Long version) {
}
