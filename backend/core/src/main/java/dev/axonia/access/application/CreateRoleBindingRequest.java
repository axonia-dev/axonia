package dev.axonia.access.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleBindingRequest(@NotBlank String subjectType, @NotBlank String subjectId, @NotNull Long roleId,
        String subjectLabel) {
}
