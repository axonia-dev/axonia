package dev.axonia.communication.domain;

/**
 * One preference row a member actually stored, its message type still the raw code: a row written by an older deploy
 * can name a type this edition no longer sends, and only the caller decides what such a row means.
 */
public record StoredPreference(String messageType, boolean enabled) {
}
