package dev.axonia;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitDecisionTest {

    @Test
    void allowCarriesNoWait() {

        var decision = RateLimitDecision.allow();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.retryAfter()).isZero();
    }

    @Test
    void refusedCarriesTheGivenWait() {

        var decision = RateLimitDecision.refused(Duration.ofSeconds(30));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void retryAfterSecondsRoundsSubSecondWaitsUpToOne() {

        var decision = RateLimitDecision.refused(Duration.ofMillis(200));

        assertThat(decision.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void retryAfterSecondsReportsWholeSecondsForLongerWaits() {

        var decision = RateLimitDecision.refused(Duration.ofSeconds(45));

        assertThat(decision.retryAfterSeconds()).isEqualTo(45);
    }

    /**
     * Rounded up, not truncated: a client told to wait 5 seconds for a token that arrives at 5.4 comes back to a second
     * refusal, which is the header lying rather than the client misbehaving.
     */
    @Test
    void retryAfterSecondsRoundsAPartialSecondUpSoATokenAlwaysExistsByThen() {

        var decision = RateLimitDecision.refused(Duration.ofMillis(5_400));

        assertThat(decision.retryAfterSeconds()).isEqualTo(6);
    }

    @Test
    void retryAfterSecondsRoundsUpEvenTheSmallestFraction() {

        var decision = RateLimitDecision.refused(Duration.ofSeconds(45).plusNanos(1));

        assertThat(decision.retryAfterSeconds()).isEqualTo(46);
    }
}
