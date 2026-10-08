package dev.axonia.organization;

import lombok.Builder;

/**
 * Domain model for the Organization registry entry. {@code id} equals the organization identifier. {@code kind}
 * distinguishes enterprise tenants from self-serve Community Hubs. {@code lifecycleState} tracks whether the
 * Organization is active, suspended, or closed. {@code communicationsEnabled} is the organization-wide kill switch for
 * outbound member messaging - every message type the edition sends, since the delivery gate checks it before it
 * resolves a row's type at all: on by default, it lets any tenant, enterprise or hub, stop all of it for every member
 * in one place.
 *
 * <p>
 * <strong>{@code communicationsEnabled} has no {@code @Builder.Default}</strong> - Lombok's {@code @Builder.Default}
 * does not apply to record components, so a {@code Organization.builder()...build()} that never calls
 * {@code .communicationsEnabled(...)} yields {@code false}, the opposite of the on-by-default design intent. Only
 * {@code OrganizationService}'s private mapper builds this in production, always setting it explicitly from the entity;
 * a test stub built without it silently means "communications off".
 */
@Builder(toBuilder = true)
public record Organization(String id, String name, OrganizationKind kind, OrganizationLifecycleState lifecycleState,
        boolean communicationsEnabled) {
}
