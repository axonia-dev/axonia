package dev.axonia.selfhosted.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.nimbusds.jose.jwk.source.OutageTolerantJWKSetSource;
import com.nimbusds.jose.jwk.source.RefreshAheadCachingJWKSetSource;

import dev.axonia.selfhosted.SelfHostedSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds that the decoder core's bearer resolver receives reads its keys through the {@link IssuerKeys} bean, the one
 * the application closes.
 */
@SelfHostedSpringBootTest
class IssuerKeysWiringTest {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private IssuerKeys issuerKeys;

    @Test
    void theInstallationDecodesWithTheIssuersKeys() {

        // when / then
        assertThat(jwtDecoder).extracting("jwtProcessor.JWSKeySelector.JWKSource").isSameAs(issuerKeys);
    }

    @Test
    void theIssuersKeysAreRefreshedAheadOfTheirLapseAndOutliveAFailingIssuerByFifteenMinutes() {

        // when
        var held = assertThat(jwtDecoder).extracting("jwtProcessor.JWSKeySelector.JWKSource.held.JWKSetSource");

        // then - kept five minutes, refreshed on a schedule 35 s before they lapse, waited on at most five seconds
        held.isInstanceOf(RefreshAheadCachingJWKSetSource.class)
                .extracting("timeToLive", "refreshAheadTime", "cacheRefreshTimeout")
                .containsExactly(300_000L, 30_000L, 5_000L);
        held.extracting("scheduledExecutorService").isNotNull();
        held.extracting("source").isInstanceOf(OutageTolerantJWKSetSource.class).extracting("timeToLive")
                .isEqualTo(900_000L);
    }
}
