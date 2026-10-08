package dev.axonia.events;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Add a slot and sign yourself up to present it. Give it a {@code topicId} to fill it from the backlog, or {@code tags}
 * to say what a talk that was never proposed is about. Supplying both is refused.
 */
public record AddSlotRequest(@NotNull String title, Long topicId, @NotNull @Positive Integer durationMinutes,
        String notes, List<String> tags) {
}
