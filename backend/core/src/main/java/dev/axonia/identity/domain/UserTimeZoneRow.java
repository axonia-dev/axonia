package dev.axonia.identity.domain;

/** One member's effective clock, as the time-zone read projects it. */
public record UserTimeZoneRow(String userId, String timeZone) {
}
