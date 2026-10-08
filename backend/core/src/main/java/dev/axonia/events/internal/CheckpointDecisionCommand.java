package dev.axonia.events.internal;

import java.time.Instant;

import dev.axonia.events.CheckpointOutcome;
import lombok.Builder;

/**
 * What {@link ActionItemLifecycle#decide} needs to log and apply one checkpoint decision - a parameter record rather
 * than a five-argument method, per this repository's five-parameter ceiling.
 */
@Builder
public record CheckpointDecisionCommand(ActionItemEntity item, Long sessionId, CheckpointOutcome outcome,
        String decidedByUserId, Instant decidedAt) {
}
