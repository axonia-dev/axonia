package dev.axonia.roundup.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/**
 * The organization's one roundup control. {@code Boolean} rather than {@code boolean} so that a body which says nothing
 * at all is rejected: absent would otherwise bind as {@code false} and read as a deliberate "switch it off".
 */
public record UpdateRoundupEnabledRequest(@NotNull Boolean enabled) {
}
