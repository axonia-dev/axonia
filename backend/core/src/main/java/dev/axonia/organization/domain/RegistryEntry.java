package dev.axonia.organization.domain;

import java.time.Instant;

import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import lombok.Builder;

/**
 * One Organization's registry row as the registry holds it, every column included, so an update round-trips exactly as
 * the stored row did.
 */
@Builder(toBuilder = true)
public record RegistryEntry(String id, String name, OrganizationKind kind, OrganizationLifecycleState lifecycleState,
        Instant closedAt, String closedBy, boolean communicationsEnabled, Instant createdAt, Instant updatedAt) {
}
