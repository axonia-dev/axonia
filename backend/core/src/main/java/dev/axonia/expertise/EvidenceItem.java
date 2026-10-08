package dev.axonia.expertise;

import java.time.Instant;

import lombok.Builder;

@Builder
public record EvidenceItem(SignalType signalType, SourceType sourceType, Long sourceId, String label,
        String communitySlug, Instant occurredAt, Long sessionId, Long typeId, String typeLabel, String typeIcon,
        String typeAccentColor) {
}
