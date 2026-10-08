package dev.axonia.knowledge;

import lombok.Builder;

@Builder
public record LibraryStats(long total, long sessionCount, long capturedThisMonth) {
}
