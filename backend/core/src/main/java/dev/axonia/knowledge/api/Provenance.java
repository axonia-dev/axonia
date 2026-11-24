package dev.axonia.knowledge.api;

import java.time.LocalDate;

import lombok.Builder;

/**
 * Where an artifact was captured. {@code label} is the slot title frozen at capture; {@code seriesTitle} and
 * {@code heldOn} are read live from the session, and are both {@code null} once that session is gone.
 */
@Builder
public record Provenance(Long sessionId, Long segmentId, String label, String seriesTitle, LocalDate heldOn) {
}
