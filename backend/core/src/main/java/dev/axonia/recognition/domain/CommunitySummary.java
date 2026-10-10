package dev.axonia.recognition.domain;

/**
 * A community's public identity as the recognition context needs it: slug for deep-linking and name for display.
 */
public record CommunitySummary(String slug, String name) {
}
