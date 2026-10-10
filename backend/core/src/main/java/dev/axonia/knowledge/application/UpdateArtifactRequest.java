package dev.axonia.knowledge.application;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateArtifactRequest(@NotBlank String title, @NotNull Long typeId,
        @NotBlank @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String url, String description,
        Set<String> tags) {
}
