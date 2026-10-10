package dev.axonia.recognition.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * One entry of an Organization's own reaction vocabulary as the catalog stores it, every column carried so an update
 * round-trips exactly. {@link ReactionDefinition} is what a caller sees of it.
 */
@Builder(toBuilder = true)
public record CustomReaction(Long id, String organizationId, String name, String imageKey, String state,
        String proposedByUserId, String approvedByUserId, Instant createdAt, Instant updatedAt, Long version) {

    /** The two states a definition can be in. Declined is a delete, not a state. */
    public static final String PENDING = "PENDING";

    public static final String APPROVED = "APPROVED";
}
