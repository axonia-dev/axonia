package dev.axonia.identity.connection;

import java.time.Duration;
import java.util.Set;

/** A freshly minted {@code axa_} access token and its paired {@code axr_} refresh token. */
public record IssuedTokens(String accessToken, String refreshToken, Duration expiresIn, Set<String> scopes) {
}
