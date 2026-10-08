package dev.axonia;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CurrentTokenTest {

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
        CurrentToken.setAllowDefaultFallback(false);
    }

    @Test
    void subject() {

        // given
        authenticate(token().build());

        // when
        var actual = CurrentToken.subject();

        // then
        assertThat(actual).isEqualTo("alice-sub");
    }

    @Test
    void organization() {

        // given
        authenticate(token().claim("org", "acme").build());

        // when
        var actual = CurrentToken.organization();

        // then
        assertThat(actual).isEqualTo("acme");
    }

    @Test
    void tenant_missingClaimDefaults() {

        // given
        CurrentToken.setAllowDefaultFallback(true);
        authenticate(token().build());

        // when
        var actual = CurrentToken.organization();

        // then
        assertThat(actual).isEqualTo("default");
    }

    @Test
    void tenant_blankClaimDefaults() {

        // given
        CurrentToken.setAllowDefaultFallback(true);
        authenticate(token().claim("org", " ").build());

        // when
        var actual = CurrentToken.organization();

        // then
        assertThat(actual).isEqualTo("default");
    }

    @Test
    void hasOrganization_presentClaim_true() {

        // given
        authenticate(token().claim("org", "acme").build());

        // when
        var actual = CurrentToken.hasOrganization();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void hasOrganization_missingClaim_false() {

        // given
        authenticate(token().build());

        // when
        var actual = CurrentToken.hasOrganization();

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void hasOrganization_blankClaim_false() {

        // given
        authenticate(token().claim("org", " ").build());

        // when
        var actual = CurrentToken.hasOrganization();

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void claim() {

        // given
        authenticate(token().claim("email", "alice@axonia.example").build());

        // when
        var actual = CurrentToken.claim("email");

        // then
        assertThat(actual).isEqualTo("alice@axonia.example");
    }

    @Test
    void claim_missing() {

        // given
        authenticate(token().build());

        // when
        var actual = CurrentToken.claim("email");

        // then
        assertThat(actual).isNull();
    }

    @Test
    void groups_presentClaim() {

        // given
        authenticate(token().claim("groups", java.util.List.of("admins", "devs")).build());

        // when
        var actual = CurrentToken.groups();

        // then
        assertThat(actual).containsExactlyInAnyOrder("admins", "devs");
    }

    @Test
    void groups_missingClaim_returnsEmpty() {

        // given
        authenticate(token().build());

        // when
        var actual = CurrentToken.groups();

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void isDelegated_trueWhenSomebodyActsForThePerson() {

        // given
        authenticate(token().claim(CurrentToken.ACTOR_CLAIM, Map.of("sub", "actor-1")).build());

        // when / then
        assertThat(CurrentToken.isDelegated()).isTrue();
    }

    @Test
    void isDelegated_falseForThePersonsOwnSignIn() {

        // given
        authenticate(token().build());

        // when / then
        assertThat(CurrentToken.isDelegated()).isFalse();
    }

    @Test
    void isAgentToken_isTrueForAPersonalTokenClaim() {

        // given
        authenticate(token().claim("tokenId", 7L).build());

        // when
        var actual = CurrentToken.isAgentToken();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void isAgentToken_isTrueForAConnectionClaim() {

        // given
        authenticate(token().claim("connectionId", 4L).build());

        // when
        var actual = CurrentToken.isAgentToken();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void isAgentToken_isFalseForAPersonsOwnToken() {

        // given
        authenticate(token().claim("org", "acme").build());

        // when
        var actual = CurrentToken.isAgentToken();

        // then
        assertThat(actual).isFalse();
    }

    @Test
    void subject_withoutAuthenticatedJwt() {

        // when / then
        assertThatIllegalStateException().isThrownBy(CurrentToken::subject)
                .withMessageContaining("No authenticated JWT");
    }

    private static Jwt.Builder token() {

        return Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub");
    }

    private static void authenticate(Jwt jwt) {

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
