package dev.axonia.recognition;

import lombok.Builder;

@Builder
public record LoveSummary(long count, boolean viewerHasLoved) {
}
