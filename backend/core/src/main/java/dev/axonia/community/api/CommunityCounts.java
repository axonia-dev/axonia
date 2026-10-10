package dev.axonia.community.api;

/**
 * How many communities an Organization is currently running. Archived communities do not count. Published so a context
 * that meters communities can read the number without knowing how a community is stored.
 */
public interface CommunityCounts {

    int countActive(String organizationId);
}
