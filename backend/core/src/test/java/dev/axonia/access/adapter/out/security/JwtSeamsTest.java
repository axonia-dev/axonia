package dev.axonia.access.adapter.out.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.identity.api.DirectoryGroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtSeamsTest {

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtOrganizationContextReturnsTheTenantClaim() {

        // given
        authenticate();
        var sut = new JwtOrganizationContext();

        // when / then
        assertThat(sut.organizationId()).isEqualTo("acme");
    }

    @Test
    void currentTokenCallerIdentityReturnsSubject() {

        // given
        authenticate();
        var sut = new CurrentTokenCallerIdentity(mock(DirectoryGroups.class));

        // when / then
        assertThat(sut.subject()).isEqualTo("alice-sub");
    }

    @Test
    void currentTokenCallerIdentityReturnsTenant() {

        // given
        authenticate();
        var sut = new CurrentTokenCallerIdentity(mock(DirectoryGroups.class));

        // when / then
        assertThat(sut.organizationId()).isEqualTo("acme");
    }

    @Test
    void currentTokenCallerIdentityReturnsGroups() {

        // given
        authenticateWithGroups(List.of("Engineering"));
        var sut = new CurrentTokenCallerIdentity(mock(DirectoryGroups.class));

        // when / then
        assertThat(sut.groups()).containsExactly("Engineering");
    }

    private static void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static void authenticateWithGroups(List<String> groups) {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme")
                .claim("groups", groups).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
