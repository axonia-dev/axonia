package dev.axonia.access;

import java.util.List;

import lombok.Builder;

@Builder
public record EffectiveRole(String role, String via, Long bindingId, Long roleId, List<String> permissions) {
    public EffectiveRole {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
