package dev.axonia.organization.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class JwtOrganizationContextTest {

    private final JwtOrganizationContext sut = new JwtOrganizationContext();

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void organizationId_readsTheOrgClaimOfTheCurrentToken() {

        assertThat(sut.organizationId()).isEqualTo("acme");
    }
}
