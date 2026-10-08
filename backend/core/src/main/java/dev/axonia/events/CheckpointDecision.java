package dev.axonia.events;

import jakarta.validation.constraints.NotNull;

/**
 * One answer given during a session's follow-up pass: this action item, this outcome. Both are required: a null outcome
 * reaches {@code ActionItemLifecycle}'s switch and fails there rather than as a rejected request.
 */
public record CheckpointDecision(@NotNull Long actionItemId, @NotNull CheckpointOutcome outcome) {
}
