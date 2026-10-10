package dev.axonia.events.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Pulls a backlog topic into a section as an OPEN (presenter-less) slot awaiting a volunteer.
// title is optional (defaults to the topic title); notes is optional (defaults to the topic description).
public record QueueTopicRequest(@NotNull Long topicId, @NotNull @Positive Integer durationMinutes, String title,
        String notes) {
}
