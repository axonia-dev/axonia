package dev.axonia.recognition.application;

import dev.axonia.recognition.domain.ReactionKind;
import lombok.Builder;

/** One chip in the strip. {@code name} and {@code imageUrl} are null for a standard emoji. */
@Builder
public record ReactionTally(ReactionKind kind, String emoji, Long definitionId, String name, String imageUrl, int count,
        boolean viewerHasReacted) {
}
