package dev.axonia.expertise.domain;

import java.time.Instant;

/**
 * Input row for replacing a source's signals. {@code rawTag} will be normalised by the writer;
 * blank-after-normalisation tags are silently dropped.
 */
public record SignalRow(String userId, String rawTag, SignalType signalType, Instant occurredAt) {
}
