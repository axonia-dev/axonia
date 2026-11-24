package dev.axonia.knowledge.domain;

import java.time.LocalDate;

import lombok.Builder;

/**
 * A session at least one artifact is attributed to, with how many. {@code name} and {@code heldOn} are {@code null}
 * until labelled, and stay so for a session that no longer resolves.
 */
@Builder(toBuilder = true)
public record ArtifactSession(Long sessionId, long count, String name, LocalDate heldOn) {
}
