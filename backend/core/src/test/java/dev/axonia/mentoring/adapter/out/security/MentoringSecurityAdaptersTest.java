package dev.axonia.mentoring.adapter.out.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringSecurityAdaptersTest {

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserIsTheTokenSubject() {

        // given
        authenticate(Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme"));

        // when / then
        assertThat(new JwtCurrentUser().userId()).isEqualTo("alice-sub");
        assertThat(new JwtCurrentUser().delegated()).isFalse();
    }

    @Test
    void aTokenWithAnActorIsDelegated() {

        // given
        authenticate(Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("act", "support"));

        // when / then
        assertThat(new JwtCurrentUser().delegated()).isTrue();
    }

    @Test
    void organizationIsTheTenantClaim() {

        // given
        authenticate(Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme"));

        // when / then
        assertThat(new JwtOrganizationContext().organizationId()).isEqualTo("acme");
    }

    private static void authenticate(Jwt.Builder jwt) {

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }
}
