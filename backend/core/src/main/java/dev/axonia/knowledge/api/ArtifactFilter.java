package dev.axonia.knowledge.api;

import java.time.Instant;

import lombok.Builder;

/**
 * The published query language of the knowledge module's artifact library. Consumed by the HTTP adapter
 * ({@link ArtifactSearchRequest}) and by other modules (the agent MCP surface) alike; internal repositories depend
 * inward on it.
 */
@Builder
public record ArtifactFilter(String q, Long typeId, String tag, String contributor, Instant from, Instant to,
        Long session, String sort) {
}
