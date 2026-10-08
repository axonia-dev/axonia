package dev.axonia.events.internal;

import java.security.NoSuchAlgorithmException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTokensTest {

    @Test
    void mintsAUrlSafeTokenWithNoPadding() {

        var token = SubscriptionTokens.mint();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void neverMintsTheSameTokenTwice() {

        var minted = IntStream.range(0, 500).mapToObj(i -> SubscriptionTokens.mint()).distinct().count();

        assertThat(minted).isEqualTo(500);
    }

    @Test
    void hashesToSixtyFourLowercaseHexCharacters() {

        assertThat(SubscriptionTokens.hash("abc")).matches("[0-9a-f]{64}");
    }

    @Test
    void theSameTokenAlwaysHashesTheSameWay() {

        var token = SubscriptionTokens.mint();

        assertThat(SubscriptionTokens.hash(token)).isEqualTo(SubscriptionTokens.hash(token));
    }

    @Test
    void theOneArgumentHashIsTheSha256One() {

        assertThat(SubscriptionTokens.hash("abc")).isEqualTo(SubscriptionTokens.hash("SHA-256", "abc"));
    }

    /**
     * SHA-256 is mandated always-available, so only a name no JCA provider registers can reach the catch the checked
     * exception forces.
     */
    @Test
    void refusesToHashWithAnAlgorithmThisJvmDoesNotHave() {

        assertThatThrownBy(() -> SubscriptionTokens.hash("NoSuchDigest", "abc"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NoSuchDigest")
                .hasCauseInstanceOf(NoSuchAlgorithmException.class);
    }
}
