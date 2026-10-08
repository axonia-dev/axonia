package dev.axonia.identity.internal;

/** One member's effective clock, as {@link UserRepository#findTimeZones} projects it. */
public record UserTimeZoneRow(String userId, String timeZone) {
}
