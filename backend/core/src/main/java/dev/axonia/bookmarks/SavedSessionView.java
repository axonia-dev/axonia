package dev.axonia.bookmarks;

import java.time.Instant;

import lombok.Builder;

@Builder
public record SavedSessionView(SavedSession session, CommunitySummary community,
        Instant savedAt) implements SavedItemView {
}
