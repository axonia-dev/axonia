package dev.axonia.organization;

import jakarta.validation.constraints.NotNull;

/** The organization-wide kill switch for outbound member messaging. See {@link Organization#communicationsEnabled}. */
public record UpdateCommunicationsEnabledRequest(@NotNull Boolean enabled) {
}
