package dev.axonia.health;

/**
 * One active series' rhythm hint as health needs it: the expected gap between sessions in days. Every active series has
 * a concrete cadence, so this is always populated. The nullable type is retained for the classifier's own no-median
 * fallback, not for an "ad-hoc" series.
 */
public record SeriesPulse(Integer cadenceDays) {
}
