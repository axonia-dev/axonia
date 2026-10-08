package dev.axonia.recognition;

import jakarta.validation.constraints.NotNull;

public record ReactionRequest(@NotNull ContributionType contributionType, @NotNull Long contributionId,
        @NotNull ReactionKind kind, String emoji, Long definitionId) {

    public ReactionRef ref() {

        return new ReactionRef(kind, emoji, definitionId);
    }
}
