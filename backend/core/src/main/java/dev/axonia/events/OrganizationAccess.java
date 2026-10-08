package dev.axonia.events;

public interface OrganizationAccess {

    /**
     * Whether the Organization is still operating. The calendar feed asks because it is served from {@code /public/**},
     * which {@code OrganizationLifecycleFilter} does not cover, so a suspended or closed tenant would otherwise keep
     * publishing its schedule to every subscriber indefinitely.
     */
    boolean isActive(String organizationId);
}
