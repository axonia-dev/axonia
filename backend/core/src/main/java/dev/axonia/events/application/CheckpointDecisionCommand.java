package dev.axonia.events.application;

import java.time.Instant;

import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.CheckpointOutcome;
import lombok.Builder;

/**
 * What {@link ActionItemLifecycle#decide} needs to log and apply one checkpoint decision - a parameter record rather
 * than a five-argument method, per this repository's five-parameter ceiling.
 */
@Builder
public record CheckpointDecisionCommand(ActionItemRecord item, Long sessionId, CheckpointOutcome outcome,
        String decidedByUserId, Instant decidedAt) {
}
