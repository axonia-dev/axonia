package dev.axonia.bookmarks.domain;

/** Minimal community identity for provenance badges on saved items. */
public record CommunitySummary(Long id, String slug, String name, String accentColor) {
}
