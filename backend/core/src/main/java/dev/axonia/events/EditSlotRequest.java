package dev.axonia.events;

import java.util.List;

import jakarta.validation.constraints.Positive;

// durationMinutes is editable (was frozen-at-creation in Milestone 1); null means "keep current".
// tags replace the slot's own subject wholesale, and are refused alongside a topicId: a slot filled from a topic takes
// its subject from the topic.
public record EditSlotRequest(String title, Long topicId, String notes, @Positive Integer durationMinutes,
        List<String> tags) {
}
