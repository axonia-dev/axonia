package dev.axonia.access;

import java.util.Set;

import lombok.Builder;

@Builder
public record Role(Long id, String name, String description, boolean system, String slug, Set<Permission> permissions) {
}
