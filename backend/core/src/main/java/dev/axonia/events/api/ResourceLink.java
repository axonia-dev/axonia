package dev.axonia.events.api;

/**
 * A single resource as a wrap-up or session page shows it: the artifact's own title, not its id, alongside the URL the
 * templates already know how to guard - rendered as a link only for {@code http://} or {@code https://}, otherwise as
 * plain text.
 */
public record ResourceLink(String title, String url) {
}
