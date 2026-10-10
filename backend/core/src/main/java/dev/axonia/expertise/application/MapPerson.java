package dev.axonia.expertise.application;

/** A contributor as the map needs them: enough to draw a face and name it, and nothing else. */
public record MapPerson(String userId, String displayName, String avatarUrl) {
}
