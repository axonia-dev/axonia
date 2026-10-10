package dev.axonia.recognition.application;

import lombok.Builder;

@Builder
public record LoveSummary(long count, boolean viewerHasLoved) {
}
