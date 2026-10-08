package dev.axonia.selfhosted.internal;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;

import dev.axonia.selfhosted.SelfHostedProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The algorithms the installation's decoder verifies a token with: every RSA and EC signature its issuer's keys can
 * make, and nothing a token can choose to make the issuer's public key verify.
 */
class TokenAlgorithmsTest {

    private final LocalIssuer issuer = LocalIssuer.start();

    private final ECKey ec = issuer.publish(LocalIssuer.ecKey("ec", Curve.P_256));

    private final SelfHostedSecurityConfig config = new SelfHostedSecurityConfig();

    private final IssuerKeys keys = config.issuerKeys(properties(issuer.uri()), Clock.systemUTC());

    private final JwtDecoder decoder = config.jwtDecoder(keys, properties(issuer.uri()), Clock.systemUTC());

    @AfterEach
    void stop() throws IOException {

        keys.close();
        issuer.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512"})
    void aTokenSignedWithTheIssuersRsaKeyIsAccepted(String algorithm) throws JOSEException {

        // given
        var token = issuer.token(JWSAlgorithm.parse(algorithm), "first", new RSASSASigner(issuer.first));

        // when
        var actual = decoder.decode(token);

        // then
        assertThat(actual.getSubject()).isEqualTo("alice");
    }

    @ParameterizedTest
    @CsvSource({"ES256, P-256", "ES384, P-384", "ES512, P-521"})
    void aTokenSignedWithAnIssuersEcKeyIsAccepted(String algorithm, String curve) throws JOSEException {

        // given
        var key = issuer.publish(LocalIssuer.ecKey("ec-" + curve, Curve.parse(curve)));
        var token = issuer.token(JWSAlgorithm.parse(algorithm), key.getKeyID(), new ECDSASigner(key));

        // when
        var actual = decoder.decode(token);

        // then
        assertThat(actual.getSubject()).isEqualTo("alice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS256", "HS384", "HS512"})
    void aTokenMacedWithTheIssuersPublicKeyIsRefusedWithoutAskingForKeys(String algorithm) throws JOSEException {

        // given
        var token = issuer.token(JWSAlgorithm.parse(algorithm), "first",
                new MACSigner(issuer.first.toRSAPublicKey().getEncoded()));

        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
        assertThat(issuer.discoveryFetches).hasValue(0);
        assertThat(issuer.keyFetches).hasValue(0);
    }

    @Test
    void anUnsignedTokenIsRefused() {

        // given
        var token = issuer.unsigned();

        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void aTokenWhoseAlgorithmDoesNotFitTheKeyItNamesIsRefused() throws JOSEException {

        // given
        var rsaNamingTheEcKey = issuer.token(JWSAlgorithm.RS256, "ec", new RSASSASigner(issuer.first));
        var ecNamingTheRsaKey = issuer.token(JWSAlgorithm.ES256, "first", new ECDSASigner(ec));

        // when / then
        assertThatThrownBy(() -> decoder.decode(rsaNamingTheEcKey)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(ecNamingTheRsaKey)).isInstanceOf(BadJwtException.class);
    }

    private static SelfHostedProperties properties(String issuer) {

        return new SelfHostedProperties(new SelfHostedProperties.Organization("acme", "Acme"), "owners",
                new SelfHostedProperties.Oidc(issuer, LocalIssuer.AUDIENCE, "sub", "groups", List.of("email"), "name"));
    }
}
