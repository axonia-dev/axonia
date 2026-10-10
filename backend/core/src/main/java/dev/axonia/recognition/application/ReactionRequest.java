package dev.axonia.recognition.application;

import jakarta.validation.constraints.NotNull;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.domain.ReactionKind;
import dev.axonia.recognition.domain.ReactionRef;

public record ReactionRequest(@NotNull ContributionType contributionType, @NotNull Long contributionId,
        @NotNull ReactionKind kind, String emoji, Long definitionId) {

    public ReactionRef ref() {

        return new ReactionRef(kind, emoji, definitionId);
    }
}
