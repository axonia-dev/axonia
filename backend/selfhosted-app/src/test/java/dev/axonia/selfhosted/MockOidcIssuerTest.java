package dev.axonia.selfhosted;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The issuer's tokens, read by the same Spring Security decoder an installation builds from discovery: signature
 * against the published keys, and issuer.
 */
class MockOidcIssuerTest {

    private static final MockOidcIssuer ISSUER = MockOidcIssuer.shared();

    private static final JwtDecoder DECODER = NimbusJwtDecoder.withIssuerLocation(ISSUER.issuerUri()).build();

    @Test
    void aTokenCarriesItsSubjectEveryClaimAndTheDefaultAudience() {

        // when
        var jwt = DECODER.decode(ISSUER.token("alice",
                Map.of("groups", List.of("axonia-owners"), "email", "alice@acme.test", "org", "elsewhere")));

        // then
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsStringList("groups")).containsExactly("axonia-owners");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("alice@acme.test");
        assertThat(jwt.getClaimAsString("org")).isEqualTo("elsewhere");
        assertThat(jwt.getAudience()).containsExactly(MockOidcIssuer.AUDIENCE);
        assertThat(jwt.getIssuer()).hasToString(ISSUER.issuerUri());
    }

    @Test
    void anAudienceInTheClaimsReplacesTheDefault() {

        assertThat(DECODER.decode(ISSUER.token("alice", Map.of("aud", "another-application"))).getAudience())
                .containsExactly("another-application");
        assertThat(DECODER.decode(ISSUER.token("alice", Map.of("aud", List.of("first", "second")))).getAudience())
                .containsExactly("first", "second");
    }

    @Test
    void anEmptyAudienceGivesATokenWithNone() {

        assertThat(DECODER.decode(ISSUER.token("alice", Map.of("aud", List.of()))).getAudience()).isNullOrEmpty();
    }

    @Test
    void noSubjectGivesATokenWithNone() {

        // when
        var jwt = DECODER.decode(ISSUER.token(null, Map.of("oid", "00000000-0000-0000-0000-000000000001")));

        // then
        assertThat(jwt.getSubject()).isNull();
        assertThat(jwt.getClaimAsString("oid")).isEqualTo("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void aForeignTokenIsRefusedByADecoderOfThisIssuer() {

        assertThatThrownBy(() -> DECODER.decode(ISSUER.foreignToken("alice", Map.of())))
                .isInstanceOf(JwtException.class);
    }
}
