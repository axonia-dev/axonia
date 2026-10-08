package dev.axonia.selfhosted.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.selfhosted.SelfHostedProperties;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelfHostedJwtConverterTest {

    private static final Instant ISSUED = Instant.parse("2026-10-02T10:00:00Z");

    private static final SelfHostedProperties DEFAULTS = configured("sub", "groups",
            List.of("email", "preferred_username"), "name");

    @Test
    void readsTheSubjectFromTheConfiguredClaim() {

        // when
        var actual = convert(configured("oid", "groups", List.of("email"), "name"),
                Map.of("sub", "pairwise-value", "oid", "o-1"));

        // then
        assertThat(actual.getToken().getSubject()).isEqualTo("o-1");
        assertThat(actual.getName()).isEqualTo("o-1");
        assertThat(actual.getAuthorities()).isEmpty();
    }

    @Test
    void refusesATokenWithNoSubjectClaim() {

        // when / then
        assertThatThrownBy(() -> convert(configured("oid", "groups", List.of("email"), "name"), Map.of("sub", "x")))
                .isInstanceOf(InvalidBearerTokenException.class).hasMessage("The token carries no oid claim.");
    }

    @Test
    void refusesATokenWithABlankSubjectClaim() {

        // when / then
        assertThatThrownBy(() -> convert(DEFAULTS, Map.of("sub", "  "))).isInstanceOf(InvalidBearerTokenException.class)
                .hasMessage("The token carries no sub claim.");
    }

    @Test
    void orgIsTheConfiguredOrganizationWhateverTheTokenSays() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "org", "evil"));

        // then
        assertThat(actual.getToken().getClaimAsString("org")).isEqualTo("acme");
    }

    @Test
    void readsGroupsFromTheConfiguredClaim() {

        // when
        var actual = convert(configured("sub", "roles", List.of("email"), "name"),
                Map.of("sub", "alice", "groups", List.of("ignored"), "roles", List.of("g-1", "g-2")));

        // then
        assertThat(actual.getToken().getClaimAsStringList("groups")).containsExactly("g-1", "g-2");
    }

    @Test
    void groupOverageYieldsNoGroupsRatherThanFailing() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "_claim_names", Map.of("groups", "src1")));

        // then
        assertThat(actual.getToken().getClaimAsStringList("groups")).isEmpty();
        assertThat(actual.getToken().hasClaim("_claim_names")).isFalse();
    }

    @Test
    void aSingleGroupAsAStringIsOneGroup() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "groups", "g-1"));

        // then
        assertThat(actual.getToken().getClaimAsStringList("groups")).containsExactly("g-1");
    }

    @Test
    void groupsThatAreNotStringsAreDropped() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "groups", List.of("g-1", 7, Map.of("id", "g-2"))));

        // then
        assertThat(actual.getToken().getClaimAsStringList("groups")).containsExactly("g-1");
    }

    @Test
    void aGroupsClaimOfAnotherShapeIsNoGroup() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "groups", 7));

        // then
        assertThat(actual.getToken().getClaimAsStringList("groups")).isEmpty();
    }

    @Test
    void theFirstConfiguredEmailClaimWins() {

        // when
        var actual = convert(DEFAULTS,
                Map.of("sub", "alice", "email", "alice@acme.test", "preferred_username", "alice.upn@acme.test"));

        // then
        assertThat(actual.getToken().getClaimAsString("email")).isEqualTo("alice@acme.test");
    }

    @Test
    void emailFallsBackToPreferredUsername() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "email", " ", "preferred_username", "alice@acme.test"));

        // then
        assertThat(actual.getToken().getClaimAsString("email")).isEqualTo("alice@acme.test");
    }

    @Test
    void anEmailTheTokenCallsUnverifiedFallsThroughToTheNextEmailClaim() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "email", "bob@acme.test", "email_verified", false,
                "preferred_username", "alice@acme.test"));

        // then
        assertThat(actual.getToken().getClaimAsString("email")).isEqualTo("alice@acme.test");
    }

    @Test
    void anEmailTheTokenCallsUnverifiedAsTextIsNoEmail() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "email", "bob@acme.test", "email_verified", "false"));

        // then
        assertThat(actual.getToken().hasClaim("email")).isFalse();
    }

    @Test
    void anEmailTheTokenCallsUnverifiedInAnyCaseIsNoEmail() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "email", "bob@acme.test", "email_verified", "False"));

        // then
        assertThat(actual.getToken().hasClaim("email")).isFalse();
    }

    @Test
    void anUnverifiedAddressEchoedInAnotherEmailClaimIsNoEmail() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "mallory", "email", "Carol@acme.test", "email_verified", false,
                "preferred_username", "carol@ACME.test"));

        // then - the username carries the very address the token calls unverified
        assertThat(actual.getToken().hasClaim("email")).isFalse();
    }

    @Test
    void anUnverifiedAddressIsRefusedWhateverSpaceSurroundsIt() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "mallory", "email", " carol@acme.test ", "email_verified",
                "false ", "preferred_username", "carol@acme.test"));

        // then
        assertThat(actual.getToken().hasClaim("email")).isFalse();
    }

    @Test
    void theEmailIsReadWithoutTheSpaceAroundIt() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "email", " alice@acme.test "));

        // then
        assertThat(actual.getToken().getClaimAsString("email")).isEqualTo("alice@acme.test");
    }

    @Test
    void anEmailTheTokenCallsVerifiedIsTheEmail() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "email", "alice@acme.test", "email_verified", true,
                "preferred_username", "alice.upn@acme.test"));

        // then
        assertThat(actual.getToken().getClaimAsString("email")).isEqualTo("alice@acme.test");
    }

    @Test
    void anUnverifiedEmailLeavesAnotherEmailClaimAlone() {

        // when
        var actual = convert(configured("sub", "groups", List.of("upn"), "name"),
                Map.of("sub", "alice", "upn", "alice@acme.test", "email_verified", false));

        // then
        assertThat(actual.getToken().getClaimAsString("email")).isEqualTo("alice@acme.test");
    }

    @Test
    void noEmailClaimLeavesNoEmail() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice"));

        // then
        assertThat(actual.getToken().hasClaim("email")).isFalse();
    }

    @Test
    void readsTheNameFromTheConfiguredClaim() {

        // when
        var actual = convert(configured("sub", "groups", List.of("email"), "display_name"),
                Map.of("sub", "alice", "name", "ignored", "display_name", "Alice Example"));

        // then
        assertThat(actual.getToken().getClaimAsString("name")).isEqualTo("Alice Example");
    }

    @Test
    void noNameClaimLeavesNoName() {

        // when
        var actual = convert(DEFAULTS, Map.of("sub", "alice", "name", ""));

        // then
        assertThat(actual.getToken().hasClaim("name")).isFalse();
    }

    @Test
    void dropsEveryClaimItDoesNotMap() {

        // when
        var actual = convert(DEFAULTS,
                Map.ofEntries(entry("sub", "alice"), entry("email", "alice@acme.test"), entry("name", "Alice"),
                        entry("groups", List.of("g-1")), entry("tokenId", "9"), entry("connectionId", "c-1"),
                        entry("tid", "t-1"), entry("picture", "https://pictures.example.com/a"), entry("sid", "s-1"),
                        entry("given_name", "Alice"), entry("preferred_username", "alice"),
                        entry("email_verified", true)));

        // then
        assertThat(actual.getToken().getClaims()).containsOnlyKeys("iss", "aud", "exp", "iat", "sub", "org", "groups",
                "email", "name");
    }

    @Test
    void keepsTheRegisteredClaimsWhenPresent() {

        // given
        var source = token(Map.of("sub", "alice", "nbf", ISSUED, "jti", "j-1"));

        // when
        var actual = (JwtAuthenticationToken) new SelfHostedJwtConverter(DEFAULTS).convert(source);

        // then
        assertThat(actual.getToken().getClaims()).containsEntry("iss", source.getClaims().get("iss"))
                .containsEntry("aud", List.of("api://axonia")).containsEntry("exp", ISSUED.plusSeconds(3600))
                .containsEntry("iat", ISSUED).containsEntry("nbf", ISSUED).containsEntry("jti", "j-1");
        assertThat(actual.getToken().getTokenValue()).isEqualTo("token-value");
        assertThat(actual.getToken().getHeaders()).isEqualTo(source.getHeaders());
    }

    private static JwtAuthenticationToken convert(SelfHostedProperties properties, Map<String, Object> claims) {

        return (JwtAuthenticationToken) new SelfHostedJwtConverter(properties).convert(token(claims));
    }

    private static Jwt token(Map<String, Object> claims) {

        var builder = Jwt.withTokenValue("token-value").header("alg", "RS256").header("kid", "k-1")
                .issuer("https://login.example.com/tenant/v2.0").audience(List.of("api://axonia")).issuedAt(ISSUED)
                .expiresAt(ISSUED.plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static SelfHostedProperties configured(String subjectClaim, String groupsClaim, List<String> emailClaims,
            String nameClaim) {

        return new SelfHostedProperties(new SelfHostedProperties.Organization("acme", "Acme"), "owners",
                new SelfHostedProperties.Oidc("https://login.example.com/tenant/v2.0", "api://axonia", subjectClaim,
                        groupsClaim, emailClaims, nameClaim));
    }
}
