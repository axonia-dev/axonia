package dev.axonia.knowledge.api;

import java.time.Instant;
import java.util.Set;

import lombok.Builder;

/**
 * The published query language of the knowledge module's artifact library. Consumed by the HTTP adapter
 * ({@link ArtifactSearchRequest}) and by other modules (the agent MCP surface) alike; internal repositories depend
 * inward on it.
 *
 * <p>
 * {@code saved} narrows to the caller's own saves; the service resolves it into {@code ids}, which the store reads.
 */
@Builder(toBuilder = true)
public record ArtifactFilter(String q, Long typeId, String tag, String contributor, Instant from, Instant to,
        Long session, String sort, boolean saved, Set<Long> ids) {

    public ArtifactFilter {

        ids = ids == null ? null : Set.copyOf(ids);
    }
}
