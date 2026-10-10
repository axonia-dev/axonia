package dev.axonia.identity.domain;

/**
 * One avatar's bytes and content type, whatever fetched them. Shared by the Graph and the claim-URL photo sources so
 * the sync orchestrator handles one type rather than two identical ones.
 */
public record AvatarPhoto(byte[] bytes, String contentType) {
}
