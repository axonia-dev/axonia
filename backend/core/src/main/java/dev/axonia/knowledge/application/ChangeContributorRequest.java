package dev.axonia.knowledge.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Who an artifact should credit instead: a member reference resolved exactly as capture resolves one, a display name,
 * an email, or a user id. The library's picker always sends the user id.
 */
public record ChangeContributorRequest(@NotBlank String contributor) {
}
