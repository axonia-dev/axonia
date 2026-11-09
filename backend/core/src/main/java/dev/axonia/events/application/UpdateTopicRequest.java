package dev.axonia.events.application;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/** What an edit changes. The kind is not among it: it follows the speakers. */
public record UpdateTopicRequest(@NotBlank String title, String description, List<String> tags) {
}
