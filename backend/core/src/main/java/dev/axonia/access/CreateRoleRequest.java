package dev.axonia.access;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleRequest(@NotBlank String name, String description, @NotNull List<String> permissions) {

    public CreateRoleRequest {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
