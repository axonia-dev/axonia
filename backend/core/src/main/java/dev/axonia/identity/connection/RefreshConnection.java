package dev.axonia.identity.connection;

/** Rotates an {@code axr_} refresh token for a fresh token pair. */
public record RefreshConnection(String refreshToken, String clientId) {
}
