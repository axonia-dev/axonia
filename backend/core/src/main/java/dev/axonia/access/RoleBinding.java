package dev.axonia.access;

import lombok.Builder;

@Builder
public record RoleBinding(Long id, String subjectType, String subjectId, String subjectLabel, Long roleId) {
}
