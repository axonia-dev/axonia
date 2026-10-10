package dev.axonia.organization.api;

/** What governs an Organization: enterprise tenancy (SSO/SCIM, always private) or a self-serve Community Hub. */
public enum OrganizationKind {
    ENTERPRISE, HUB
}
