package dev.axonia.organization.api;

import lombok.Builder;

/** The registry facts needed to create an Organization row: opaque id, display name, and kind. */
@Builder
public record OrganizationRegistration(String id, String name, OrganizationKind kind) {
}
