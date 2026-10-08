package dev.axonia;

import java.time.Duration;

/** The outcome of one rate-limit check, carrying how long a refused caller should wait before retrying. */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    private static final RateLimitDecision ALLOW = new RateLimitDecision(true, Duration.ZERO);

    /**
     * Named {@code allow} rather than {@code allowed} - a record's component accessor and a same-named static factory
     * cannot coexist, since {@code allowed()} is already the generated accessor for the {@code allowed} component.
     */
    public static RateLimitDecision allow() {

        return ALLOW;
    }

    public static RateLimitDecision refused(Duration retryAfter) {

        return new RateLimitDecision(false, retryAfter);
    }

    /**
     * Seconds for a {@code Retry-After} header, always at least 1 so a client never reads it as "retry immediately".
     *
     * <p>
     * Rounded up, never truncated: {@code Retry-After} is a promise that a token exists by then, and truncating a wait
     * of 5.4 seconds to 5 sends an obedient client back before one does, where it is refused again and told to wait
     * again. Rounding up costs at most one second and makes the header true.
     *
     * <p>
     * The single home for this rule. Every refusal in the product routes here: every {@code *RateLimitedException}
     * carries the computed value rather than a bare {@link Duration}, and {@link RateLimiter}'s WARN reads it off the
     * decision it is about to return, so the log and the header can never disagree.
     */
    public long retryAfterSeconds() {

        var roundedUp = retryAfter.getNano() == 0 ? retryAfter.toSeconds() : retryAfter.toSeconds() + 1;
        return Math.max(1, roundedUp);
    }
}
