package dev.axonia.knowledge.api;

import jakarta.validation.constraints.NotNull;

public record AttachResourceRequest(Long segmentId, @NotNull Long artifactId) {
}
