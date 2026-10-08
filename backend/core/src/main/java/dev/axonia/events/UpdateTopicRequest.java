package dev.axonia.events;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTopicRequest(@NotNull TopicKind kind, @NotBlank String title, String description,
        List<String> tags) {
}
