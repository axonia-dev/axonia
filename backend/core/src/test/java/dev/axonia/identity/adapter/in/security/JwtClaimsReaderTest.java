package dev.axonia.identity.adapter.in.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.shared.api.security.CurrentToken;

import static org.assertj.core.api.Assertions.assertThat;

class JwtClaimsReaderTest {

    private final JwtClaimsReader sut = new JwtClaimsReader();

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
        CurrentToken.setAllowDefaultFallback(false);
    }

    @Test
    void read() {

        // given
        authenticate(token().claim("org", "acme").claim("name", "Alice Anderson").claim("preferred_username", "alice")
                .claim("email", "alice@axonia.example").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual).usingRecursiveAssertion().isEqualTo(IdentityClaims.builder().userId("alice-sub")
                .organizationId("acme").displayName("Alice Anderson").email("alice@axonia.example").build());
    }

    @Test
    void read_fallsBackToPreferredUsername() {

        // given
        authenticate(token().claim("preferred_username", "alice").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("alice");
        assertThat(actual.organizationId()).isNull();
        assertThat(actual.email()).isNull();
    }

    @Test
    void read_displayNameClaimWinsOverNameParts() {

        // given - a generic SAML customer that does send a display name must keep it, parts notwithstanding
        authenticate(token().claim("name", "Alice A. Anderson").claim("given_name", "Alice")
                .claim("family_name", "Anderson").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("Alice A. Anderson");
    }

    @Test
    void read_joinsGivenAndFamilyNameWhenNoDisplayName() {

        // given - Entra's default claims: given/family name, no display-name claim at all
        authenticate(token().claim("given_name", "Alice").claim("family_name", "Anderson")
                .claim("preferred_username", "alice@acme.onmicrosoft.com").build());

        // when
        var actual = sut.read();

        // then - the human name, not the UPN
        assertThat(actual.displayName()).isEqualTo("Alice Anderson");
    }

    @Test
    void read_usesTheOnlyNamePartPresentWithoutAStraySpace() {

        // given
        authenticate(token().claim("given_name", "Alice").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("Alice");
    }

    @Test
    void read_usesTheFamilyNameAloneWithoutALeadingSpace() {

        // given
        authenticate(token().claim("family_name", "Anderson").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("Anderson");
    }

    @Test
    void read_blankNamePartsFallThroughRatherThanYieldingASpaceOrNull() {

        // given
        authenticate(token().claim("given_name", " ").claim("family_name", "").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("alice-sub");
    }

    @Test
    void read_noOrgClaimYieldsNullOrganizationIdEvenWithDevFallbackEnabled() {

        // given - a fresh social sign-in: no org claim at all; the DEV fallback would make organization()
        // return "default", but hasOrganization() must still gate it so the profile stays claims-only.
        CurrentToken.setAllowDefaultFallback(true);
        authenticate(token().claim("email", "ada@example.dev").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.organizationId()).isNull();
    }

    @Test
    void read_fallsBackToSubject() {

        // given
        authenticate(token().build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("alice-sub");
    }

    @Test
    void read_blankNameFallsThrough() {

        // given
        authenticate(token().claim("name", " ").claim("preferred_username", " ").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.displayName()).isEqualTo("alice-sub");
    }

    @Test
    void read_sidClaimIsCarriedThrough() {

        // given
        authenticate(token().claim("sid", "session-abc").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.sid()).isEqualTo("session-abc");
    }

    @Test
    void read_noSidClaimYieldsNullRatherThanThrowing() {

        // given - a personal access token or a synthesized delegated session never carries a sid
        authenticate(token().build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.sid()).isNull();
    }

    @Test
    void read_noPictureClaimYieldsNullAvatarUrl() {

        // given
        authenticate(token().build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.avatarUrl()).isNull();
    }

    @Test
    void read_httpsPictureUrlPassesThrough() {

        // given
        authenticate(token().claim("picture", "https://cdn.example.com/avatar.png").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
    }

    @Test
    void read_httpPictureUrlIsDropped() {

        // given - the bytes are fetched server-side now, so plain HTTP is refused outright
        authenticate(token().claim("picture", "http://cdn.example.com/avatar.png").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.avatarUrl()).isNull();
    }

    @Test
    void read_oversizedPictureUrlIsDropped() {

        // given - an IdP-controlled claim longer than 2048 characters must never reach the avatar sync
        var oversized = "https://cdn.example.com/" + "a".repeat(2048);
        authenticate(token().claim("picture", oversized).build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.avatarUrl()).isNull();
    }

    @Test
    void read_dataUriPictureClaimIsDropped() {

        // given - a base64 data: photo is not a fetchable URL
        authenticate(token().claim("picture", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAE=").build());

        // when
        var actual = sut.read();

        // then
        assertThat(actual.avatarUrl()).isNull();
    }

    private static Jwt.Builder token() {

        return Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub");
    }

    private static void authenticate(Jwt jwt) {

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
