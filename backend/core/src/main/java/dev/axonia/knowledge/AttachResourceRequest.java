package dev.axonia.knowledge;

import jakarta.validation.constraints.NotNull;

public record AttachResourceRequest(Long segmentId, @NotNull Long artifactId) {
}
