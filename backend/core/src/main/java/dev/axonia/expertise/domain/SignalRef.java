package dev.axonia.expertise.domain;

/** Identifies the source (type + id) of a signal. */
public record SignalRef(SourceType sourceType, Long sourceId) {
}
