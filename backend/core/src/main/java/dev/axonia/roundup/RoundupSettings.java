package dev.axonia.roundup;

/** An organization's one roundup control. Absent means off; there is no third state. */
public record RoundupSettings(String organizationId, boolean enabled) {
}
