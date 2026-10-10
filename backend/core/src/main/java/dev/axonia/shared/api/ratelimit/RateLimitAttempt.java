package dev.axonia.shared.api.ratelimit;

import java.time.Duration;

/**
 * What a {@link RateLimitStore} made of one attempt to spend a token.
 *
 * <p>
 * {@code DEGRADED} is not a refusal and not an ordinary allow. The caller proceeds, because these budgets are abuse
 * backstops rather than security controls and a Redis blip must not become a customer-visible outage, but the decision
 * was made without the shared state and is counted and logged separately so that an unguarded surface is a visible one
 * rather than a silent one.
 */
public record RateLimitAttempt(Outcome outcome, Duration retryAfter) {

    public enum Outcome {
        ALLOWED, REFUSED, DEGRADED
    }

    private static final RateLimitAttempt ALLOWED = new RateLimitAttempt(Outcome.ALLOWED, Duration.ZERO);
    private static final RateLimitAttempt DEGRADED = new RateLimitAttempt(Outcome.DEGRADED, Duration.ZERO);

    public static RateLimitAttempt allowed() {

        return ALLOWED;
    }

    public static RateLimitAttempt refused(Duration retryAfter) {

        return new RateLimitAttempt(Outcome.REFUSED, retryAfter);
    }

    public static RateLimitAttempt degraded() {

        return DEGRADED;
    }
}
