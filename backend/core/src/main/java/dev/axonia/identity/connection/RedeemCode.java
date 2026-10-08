package dev.axonia.identity.connection;

/** Exchanges an {@code axc_} authorization code for a token pair, PKCE-bound to the verifier that started the flow. */
public record RedeemCode(String code, String clientId, String redirectUri, String codeVerifier) {
}
