package dev.axonia.access.domain;

import java.time.Instant;

import lombok.Builder;

/**
 * One subject, a {@code USER} or a directory {@code GROUP}, bound to one role in an Organization. A binding IS the
 * grant. Carries its identity, timestamps and version so that a delete round-trips exactly.
 */
@Builder(toBuilder = true)
public record SubjectBinding(Long id, String organizationId, String subjectType, String subjectId, String subjectLabel,
        Long roleId, Instant createdAt, Instant updatedAt, Long version) {
}
