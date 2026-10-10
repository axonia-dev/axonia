package dev.axonia.access.domain;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/**
 * A role as an Organization's catalogue defines it: its name, whether it is one of the two seeded system roles, and the
 * permission codes it grants. Carries its identity, timestamps and version so that an update round-trips exactly.
 */
@Builder(toBuilder = true)
public record RoleDefinition(Long id, String organizationId, String name, String description, boolean system,
        String slug, List<String> permissions, Instant createdAt, Instant updatedAt, Long version) {

    public RoleDefinition {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
