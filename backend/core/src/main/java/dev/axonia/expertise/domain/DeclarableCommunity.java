package dev.axonia.expertise.domain;

/** A community the member can declare an area in: an active one they belong to. */
public record DeclarableCommunity(long id, String slug, String name) {
}
