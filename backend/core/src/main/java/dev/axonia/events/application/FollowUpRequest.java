package dev.axonia.events.application;

import java.util.List;

import jakarta.validation.Valid;

import dev.axonia.events.domain.CheckpointDecision;

/**
 * {@code @Valid} sits on the element, not the list: bean validation does not cascade into a collection's elements
 * without it, so a decision carrying a null outcome would otherwise pass the request check and fail deep in the pass.
 *
 * <p>
 * {@code decisions} is defensively copied and normalised to an empty list rather than left null: a second follow-up
 * pass with nothing left to decide is a valid, empty request, not a missing one.
 */
public record FollowUpRequest(String notes, String recordingUrl, List<@Valid CheckpointDecision> decisions) {

    public FollowUpRequest {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
