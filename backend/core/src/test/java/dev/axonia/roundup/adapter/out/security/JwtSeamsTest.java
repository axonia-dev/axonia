package dev.axonia.roundup.adapter.out.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSeamsTest {

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtOrganizationContextReturnsTheTenantClaim() {

        // given
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var sut = new JwtOrganizationContext();

        // when
        var actual = sut.organizationId();

        // then
        assertThat(actual).isEqualTo("acme");
    }
}
