package dev.axonia.bookmarks;

import java.time.Instant;

import dev.axonia.knowledge.Artifact;
import lombok.Builder;

@Builder
public record SavedArtifactView(Artifact artifact, CommunitySummary community, Instant savedAt,
        Boolean helped) implements SavedItemView {
}
