package dev.axonia.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import dev.axonia.shared.api.validation.ValidTimeZone;

/**
 * A zone as the caller states it, whether the caller is the member or their browser.
 *
 * <p>
 * Validated on the way in rather than trusted: the value is read back by {@code ZoneId.of} on every projection and on
 * every message rendered for this member, so an unresolvable one would pass silently here and throw somewhere the
 * member could not repair it.
 */
public record TimeZoneRequest(@NotBlank @ValidTimeZone String zone) {
}
