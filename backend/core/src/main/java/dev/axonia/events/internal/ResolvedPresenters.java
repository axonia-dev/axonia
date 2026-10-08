package dev.axonia.events.internal;

import java.util.List;

import dev.axonia.events.PresenterResolution;

/** Resolved presenters: the user ids to save, and the caller's original query strings to report. */
public record ResolvedPresenters(List<String> userIds, PresenterResolution report) {
}
