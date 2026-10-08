package dev.axonia.identity;

/** Port: how the identity core learns who is calling. Driven by the JWT adapter in {@code internal/}. */
public interface ClaimsReader {

    IdentityClaims read();
}
