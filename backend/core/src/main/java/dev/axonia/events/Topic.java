package dev.axonia.events;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder
public record Topic(Long id, Long communityId, TopicKind kind, String title, String description, List<String> tags,
        String proposerUserId, String proposerDisplayName, boolean canClose, boolean canReopen, Long voteCount,
        boolean hasVoted, boolean canEdit, boolean canDelete, TopicStatus status, ScheduledFor scheduledFor,
        Instant createdAt, Instant updatedAt) {
}
