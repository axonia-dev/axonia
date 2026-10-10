package dev.axonia.events.domain;

/**
 * Where a slot lives: its tenant, community, and its own id. Groups the coordinates {@code SlotEventPublisher} needs.
 */
public record SlotEventCoords(String organizationId, Long communityId, Long slotId) {
}
