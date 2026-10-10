package dev.axonia.shared.api.security;

import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.shared.api.error.MissingOrganizationException;

import static org.assertj.core.api.Assertions.*;

class CurrentTokenOrganizationTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        CurrentToken.setAllowDefaultFallback(false);
    }

    @Test
    void rejectsMissingTenantWhenFallbackDisabled() {

        // given
        CurrentToken.setAllowDefaultFallback(false);
        authenticateWithoutTenant();

        // then
        assertThatThrownBy(CurrentToken::organization).isInstanceOf(MissingOrganizationException.class);
    }

    @Test
    void fallsBackToDefaultWhenEnabled() {

        // given
        CurrentToken.setAllowDefaultFallback(true);
        authenticateWithoutTenant();

        // then
        assertThat(CurrentToken.organization()).isEqualTo("default");
    }

    @Test
    void resolvedOrganization_claimPresent_returnsIt() {

        // given
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u1").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        // then
        assertThat(CurrentToken.resolvedOrganization()).contains("acme");
    }

    @Test
    void resolvedOrganization_missingClaim_fallbackEnabled_returnsDefault() {

        // given
        CurrentToken.setAllowDefaultFallback(true);
        authenticateWithoutTenant();

        // then
        assertThat(CurrentToken.resolvedOrganization()).contains("default");
    }

    @Test
    void resolvedOrganization_missingClaim_fallbackDisabled_returnsEmpty() {

        // given — the PROD case: no org claim (fresh social user), fallback off. Must resolve to empty, NOT throw —
        // that's what keeps org-less JWTs from 401ing every DB-touching request (login-loop regression).
        CurrentToken.setAllowDefaultFallback(false);
        authenticateWithoutTenant();

        // then
        assertThat(CurrentToken.resolvedOrganization()).isEmpty();
    }

    private void authenticateWithoutTenant() {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
