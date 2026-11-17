package dev.axonia.mentoring.api;

/** {@code area} is null for a free topic. A new topic starts in To do; its state changes through a placement. */
public record TopicRequest(String label, String area) {
}
