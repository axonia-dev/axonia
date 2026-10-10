package dev.axonia.access.application;

import java.util.Set;

import dev.axonia.access.api.Permission;
import lombok.Builder;

@Builder
public record Role(Long id, String name, String description, boolean system, String slug, Set<Permission> permissions) {
}
