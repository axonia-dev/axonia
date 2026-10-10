package dev.axonia.identity.api;

/** Port: how the identity core learns who is calling. Driven by {@code adapter.in.security.JwtClaimsReader}. */
public interface ClaimsReader {

    IdentityClaims read();
}
