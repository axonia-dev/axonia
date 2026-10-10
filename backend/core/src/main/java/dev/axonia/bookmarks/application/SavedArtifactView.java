package dev.axonia.bookmarks.application;

import java.time.Instant;

import dev.axonia.bookmarks.domain.CommunitySummary;
import dev.axonia.knowledge.api.Artifact;
import lombok.Builder;

@Builder
public record SavedArtifactView(Artifact artifact, CommunitySummary community, Instant savedAt,
        Boolean helped) implements SavedItemView {
}
