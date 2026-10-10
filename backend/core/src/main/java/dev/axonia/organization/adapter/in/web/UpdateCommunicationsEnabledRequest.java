package dev.axonia.organization.adapter.in.web;

import jakarta.validation.constraints.NotNull;

import dev.axonia.organization.api.Organization;

/** The organization-wide kill switch for outbound member messaging. See {@link Organization#communicationsEnabled}. */
public record UpdateCommunicationsEnabledRequest(@NotNull Boolean enabled) {
}
