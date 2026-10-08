package dev.axonia.identity.internal;

/**
 * One avatar's bytes and content type, whatever fetched them. Shared by {@link EntraPhotoSource} and
 * {@link ClaimUrlPhotoSource} so the sync orchestrator handles one type rather than two identical ones.
 */
record AvatarPhoto(byte[] bytes, String contentType) {
}
