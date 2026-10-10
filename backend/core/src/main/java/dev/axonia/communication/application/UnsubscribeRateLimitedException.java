package dev.axonia.communication.application;

import dev.axonia.shared.api.ratelimit.RateLimitDecision;

/** The caller has spent its unsubscribe budget for this window. Carries the wait so the 429 can be honest about it. */
public class UnsubscribeRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public UnsubscribeRateLimitedException(RateLimitDecision refusal) {

        super("Too many unsubscribe attempts. Try again shortly.");
        this.retryAfterSeconds = refusal.retryAfterSeconds();
    }

    public long retryAfterSeconds() {

        return retryAfterSeconds;
    }
}
