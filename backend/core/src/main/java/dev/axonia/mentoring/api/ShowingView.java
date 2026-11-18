package dev.axonia.mentoring.api;

/**
 * Whether the pairing shows on the community's Mentoring page: SHOWN, ASKING or PRIVATE. {@code iSaidYes} is the
 * caller's own yes while it asks.
 */
public record ShowingView(String visibility, boolean iSaidYes) {
}
