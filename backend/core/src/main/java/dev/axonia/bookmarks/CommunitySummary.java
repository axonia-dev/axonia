package dev.axonia.bookmarks;

/** Minimal community identity for provenance badges on saved items. */
public record CommunitySummary(Long id, String slug, String name, String accentColor) {
}
