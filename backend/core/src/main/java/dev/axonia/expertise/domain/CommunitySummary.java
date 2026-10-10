package dev.axonia.expertise.domain;

/**
 * A community's public identity as the expertise context needs it: slug for deep-linking and name for display.
 */
public record CommunitySummary(String slug, String name) {
}
