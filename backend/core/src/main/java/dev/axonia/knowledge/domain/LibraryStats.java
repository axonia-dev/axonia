package dev.axonia.knowledge.domain;

import lombok.Builder;

@Builder
public record LibraryStats(long total, long sessionCount, long capturedThisMonth) {
}
