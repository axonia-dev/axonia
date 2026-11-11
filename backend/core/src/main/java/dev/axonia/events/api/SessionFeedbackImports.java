package dev.axonia.events.api;

/**
 * Bringing the anonymous answers a community collected before axonia onto one held session. One import per session,
 * replaced whole by the next; an empty list removes it. Live and imported answers never mix on a session.
 */
public interface SessionFeedbackImports {

    FeedbackImportResult importFeedback(Long sessionId, FeedbackImportRequest request);
}
