package dev.axonia.bookmarks;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({@JsonSubTypes.Type(value = SavedArtifactView.class, name = "artifact"),
        @JsonSubTypes.Type(value = SavedSessionView.class, name = "session"),})
public sealed interface SavedItemView permits SavedArtifactView, SavedSessionView {
    CommunitySummary community();

    Instant savedAt();
}
