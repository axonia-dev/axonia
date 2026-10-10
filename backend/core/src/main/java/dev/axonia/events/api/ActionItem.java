package dev.axonia.events.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

@Builder
public record ActionItem(Long id, Long communityId, String title, String description, List<String> tags,
        String raisedByUserId, String raisedByDisplayName, String assigneeUserId, String assigneeDisplayName,
        LocalDate dueDate, Long originSessionId, Checkpoint checkpoint, int carryCount, ActionItemOutcome outcome,
        boolean canClose, boolean canReopen, boolean canEdit, boolean canDelete, Instant createdAt, Instant updatedAt) {
}
