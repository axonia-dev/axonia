package dev.axonia.organization.application.port.out;

/** The organization identifier the current caller's request is scoped to. */
public interface OrganizationContext {

    String organizationId();
}
