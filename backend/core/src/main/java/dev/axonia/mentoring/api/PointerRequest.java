package dev.axonia.mentoring.api;

/** Exactly one of {@code topicId} and {@code noteId} holds the pointer. */
public record PointerRequest(Long topicId, Long noteId, String url) {
}
