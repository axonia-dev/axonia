package dev.axonia.events.adapter.out.security;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.shared.api.security.CurrentToken;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSeamsTest {

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtCurrentUserReturnsTheTokenSubject() {

        // given
        authenticate();
        var sut = new JwtCurrentUser();

        // when
        var actual = sut.userId();

        // then
        assertThat(actual).isEqualTo("alice-sub");
    }

    @Test
    void jwtCurrentUserIsNotDelegatedOnTheMembersOwnSignIn() {

        // given
        authenticate();

        // when
        var actual = new JwtCurrentUser().delegated();

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void jwtCurrentUserIsDelegatedWhenSomebodyElseActs() {

        // given
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub")
                .claim(CurrentToken.ACTOR_CLAIM, Map.of("sub", "support")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        // when
        var actual = new JwtCurrentUser().delegated();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void jwtOrganizationContextReturnsTheTenantClaim() {

        // given
        authenticate();
        var sut = new JwtOrganizationContext();

        // when
        var actual = sut.organizationId();

        // then
        assertThat(actual).isEqualTo("acme");
    }

    private static void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
