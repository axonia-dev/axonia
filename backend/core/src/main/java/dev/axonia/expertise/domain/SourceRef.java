package dev.axonia.expertise.domain;

/** Identifies an activity source (a slot, artifact, or topic) that a signal was derived from. */
public record SourceRef(SourceType sourceType, Long sourceId) {
}
