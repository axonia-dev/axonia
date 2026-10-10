package dev.axonia.expertise.domain;

/**
 * A community that contributed at least one signal to a self-view area — id for filtering, slug for fetching that
 * community's reads, name for display.
 */
public record AreaCommunity(Long id, String slug, String name) {
}
