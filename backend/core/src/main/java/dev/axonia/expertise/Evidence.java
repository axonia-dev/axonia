package dev.axonia.expertise;

import java.util.List;

import lombok.Builder;

@Builder
public record Evidence(int presentedCount, int contributedCount, int offeredCount, List<EvidenceItem> recent) {
}
