package dev.axonia.recognition.application;

import jakarta.validation.constraints.NotNull;

import dev.axonia.recognition.api.ContributionType;

/**
 * A member's love for a contribution. Only the contribution reference travels on the wire; community, title,
 * occurred-at and who gets credit are resolved server-side from the owning bounded context.
 */
public record LoveRequest(@NotNull ContributionType contributionType, @NotNull Long contributionId) {
}
