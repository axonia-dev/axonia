package dev.axonia.events.api;

import java.util.List;
import java.util.Map;

import dev.axonia.events.api.spi.SessionResourceAccess;

/**
 * The resources {@link SessionResourceAccess} answers with for one session: {@code sessionLevel} for those attached to
 * the session itself and {@code bySlot} for those attached to one of its talks, keyed by slot id. A resource's home is
 * decided once, when it is attached - {@code artifact_attachment.segment_id} is either null or a real slot id, never
 * both - so a {@link ResourceLink} belongs to exactly one of these two places.
 */
public record SessionResources(List<ResourceLink> sessionLevel, Map<Long, List<ResourceLink>> bySlot) {
}
