package dev.axonia.recognition;

import jakarta.validation.constraints.NotNull;

/**
 * The withdraw-a-reaction query string, bound as one {@code @ModelAttribute} record rather than five request
 * parameters, on the same precedent {@code ArtifactSearchRequest} follows.
 *
 * <p>
 * Component names are the wire contract, not an implementation detail: the frontend gateway sends
 * {@code contributionType}, {@code contributionId}, {@code kind}, {@code emoji} and {@code definitionId} verbatim, and
 * {@code @ModelAttribute} binds by property name, so renaming any of them here would silently break the query string a
 * caller already sends.
 */
public record UnreactRequest(@NotNull ContributionType contributionType, @NotNull Long contributionId,
        @NotNull ReactionKind kind, String emoji, Long definitionId) {
}
