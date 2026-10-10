package dev.axonia.recognition.api;

/**
 * Where a presented slot sits in the schedule: the session it belongs to and the visual identity of its section type.
 * Lets the impact profile deep-link to the session and render the real section-type badge. Icon and accent colour are
 * optional (tenant-managed).
 */
public record SlotSource(Long sessionId, Long sectionTypeId, String sectionTypeLabel, String sectionTypeIcon,
        String sectionTypeAccentColor) {
}
