package dev.axonia.community.api;

/** The latest shared artifact and the latest proposed topic, each null when there is none. */
public record LobbyLately(String artifactTitle, String topicTitle) {
}
