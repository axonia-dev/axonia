package dev.axonia.events.internal;

/**
 * Where a slot lives: its tenant, community, and its own id. Groups the coordinates {@link SlotEventPublisher} needs.
 */
public record SlotEventCoords(String organizationId, Long communityId, Long slotId) {
}
