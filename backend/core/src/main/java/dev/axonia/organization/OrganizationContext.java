package dev.axonia.organization;

/** The organization identifier the current caller's request is scoped to. */
public interface OrganizationContext {

    String organizationId();
}
