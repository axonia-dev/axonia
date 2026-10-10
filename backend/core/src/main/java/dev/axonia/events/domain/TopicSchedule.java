package dev.axonia.events.domain;

import java.time.Instant;

public record TopicSchedule(Long topicId, Long sessionId, Long sectionId, Long slotId, Instant scheduledAt,
        String scheduleLabel) {
}
