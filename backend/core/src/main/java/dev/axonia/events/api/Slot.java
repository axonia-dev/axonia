package dev.axonia.events.api;

import java.util.List;

import lombok.Builder;

/**
 * A talk in a session. Its subject comes from {@code topicId} when it was filled from a backlog topic, and from
 * {@code tags} when it was not: a slot has a topic or its own tags, never both.
 */
@Builder
public record Slot(Long id, Long sectionId, Integer position, List<String> presenterUserIds, Long topicId, String title,
        String notes, Integer durationMinutes, List<String> tags) {

    public Slot {
        presenterUserIds = presenterUserIds == null ? List.of() : List.copyOf(presenterUserIds);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
