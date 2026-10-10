package dev.axonia.bookmarks.application;

import java.time.Instant;

import dev.axonia.bookmarks.domain.CommunitySummary;
import dev.axonia.bookmarks.domain.SavedSession;
import lombok.Builder;

@Builder
public record SavedSessionView(SavedSession session, CommunitySummary community,
        Instant savedAt) implements SavedItemView {
}
