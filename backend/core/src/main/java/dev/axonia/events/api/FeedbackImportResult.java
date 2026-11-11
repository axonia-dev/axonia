package dev.axonia.events.api;

/** How many imported answers hold each question, and whether the session shows its result. */
public record FeedbackImportResult(Long sessionId, long enjoyedAnswers, long learnedAnswers, boolean revealed) {
}
