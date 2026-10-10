package dev.axonia.events.application;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import dev.axonia.events.api.TopicKind;

public record UpdateTopicRequest(@NotNull TopicKind kind, @NotBlank String title, String description,
        List<String> tags) {
}
