package dev.axonia.installation.application;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.SteppableClock;
import dev.axonia.installation.domain.TestKeys;

import static org.assertj.core.api.Assertions.assertThat;

class TestTokensTest {

    private static final TestTokens.Tested TESTED = new TestTokens.Tested("fingerprint-a", 3, "ana");

    private static final Instant ACCEPTED_AT = Instant.parse("2026-10-04T10:00:00.700Z");

    private final SteppableClock clock = new SteppableClock(ACCEPTED_AT.plusSeconds(14));

    private final TestTokens tokens = new TestTokens(TestKeys.current(), clock);

    @Test
    void holdsForTheValuesTheRevisionAndThePersonTestedForTenMinutes() {

        // when
        var actual = tokens.issue(TESTED, ACCEPTED_AT);

        // then
        assertThat(actual.expiresAt()).as("from the instant accepted, not the one issued")
                .isEqualTo(Instant.parse("2026-10-04T10:10:00Z"));
        assertThat(actual.token()).startsWith("v1.bff04208.1791108600.");
        assertThat(tokens.verify(actual.token(), TESTED)).isTrue();
        clock.advance(Duration.ofMillis(599_299 - 14_000));
        assertThat(tokens.verify(actual.token(), TESTED)).as("10:09:59.999, the last instant it holds").isTrue();
        clock.advance(Duration.ofMillis(1));
        assertThat(tokens.verify(actual.token(), TESTED)).as("10:10:00.000, its expiresAt").isFalse();
    }

    @Test
    void holdsForNothingElse() {

        // given
        var token = tokens.issue(TESTED, ACCEPTED_AT).token();

        // then
        assertThat(tokens.verify(token, new TestTokens.Tested("fingerprint-b", 3, "ana"))).isFalse();
        assertThat(tokens.verify(token, new TestTokens.Tested("fingerprint-a", 4, "ana"))).isFalse();
        assertThat(tokens.verify(token, new TestTokens.Tested("fingerprint-a", 3, "bob"))).isFalse();
        assertThat(new TestTokens(TestKeys.previousOnly(), clock).verify(token, TESTED)).isFalse();
    }

    @Test
    void aLaterExpiryWrittenIntoATokenBreaksIt() {

        // given
        var parts = tokens.issue(TESTED, ACCEPTED_AT).token().split("\\.");

        // when
        var actual = tokens.verify("v1." + parts[1] + ".1791109200." + parts[3], TESTED);

        // then
        assertThat(actual).isFalse();
    }

    /** During a rotation's rolling deploy, instances on either key check each other's tokens. */
    @Test
    void holdsOnAnInstanceStillOnThePreviousKeyAndTheOtherWayRound() {

        // given
        var previousInstance = new TestTokens(TestKeys.previousOnly(), clock);
        var rotatedInstance = new TestTokens(TestKeys.currentWithPrevious(), clock);
        var issuedBeforeTheRotation = previousInstance.issue(TESTED, ACCEPTED_AT).token();

        // then
        assertThat(issuedBeforeTheRotation).startsWith("v1.e4caaa0c.");
        assertThat(rotatedInstance.verify(issuedBeforeTheRotation, TESTED)).isTrue();
        assertThat(rotatedInstance.verify(issuedBeforeTheRotation.replace("v1.e4caaa0c.", "v1.bff04208."), TESTED))
                .as("a token checks only under the key it names").isFalse();
        assertThat(tokens.verify(issuedBeforeTheRotation, TESTED)).as("a key no longer held").isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "v1", "v1.bff04208.1791108600", "v1.1791108600.abc", "v2.bff04208.1791108600.abc",
            "v1.bff04208.soon.abc", "v1.bff04208.99999999999999999999.abc", "v1.00000000.1791108600.abc"})
    void readsNothingThatIsNotAToken(String token) {

        assertThat(tokens.verify(token, TESTED)).isFalse();
    }
}
