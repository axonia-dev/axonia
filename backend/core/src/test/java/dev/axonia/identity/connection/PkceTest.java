package dev.axonia.identity.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceTest {

    /** RFC 7636 Appendix B. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void matches_theRfcVector() {
        assertThat(Pkce.matches(VERIFIER, CHALLENGE)).isTrue();
    }

    @Test
    void matches_refusesAnotherVerifier() {
        assertThat(Pkce.matches("x".repeat(43), CHALLENGE)).isFalse();
    }

    @Test
    void matches_refusesAMalformedVerifier() {
        assertThat(Pkce.matches("short", CHALLENGE)).isFalse();
        assertThat(Pkce.matches("a".repeat(129), CHALLENGE)).isFalse();
        assertThat(Pkce.matches("a".repeat(42) + "!", CHALLENGE)).isFalse();
        assertThat(Pkce.matches(null, CHALLENGE)).isFalse();
        assertThat(Pkce.matches(VERIFIER, null)).isFalse();
    }

    @Test
    void isChallenge() {
        assertThat(Pkce.isChallenge(CHALLENGE)).isTrue();
        assertThat(Pkce.isChallenge(CHALLENGE + "=")).isFalse();
        assertThat(Pkce.isChallenge("plain-text")).isFalse();
        assertThat(Pkce.isChallenge(null)).isFalse();
    }
}
