package dev.axonia.expertise;

import java.time.Instant;

import lombok.Builder;

@Builder
public record ExpertiseSignal(Long id, String organizationId, Long communityId, String userId, String tag,
        SignalType signalType, SourceType sourceType, Long sourceId, Instant occurredAt) {
}
