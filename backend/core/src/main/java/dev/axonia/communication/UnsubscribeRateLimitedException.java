package dev.axonia.communication;

import dev.axonia.RateLimitDecision;

/** The caller has spent its unsubscribe budget for this window. Carries the wait so the 429 can be honest about it. */
class UnsubscribeRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    UnsubscribeRateLimitedException(RateLimitDecision refusal) {

        super("Too many unsubscribe attempts. Try again shortly.");
        this.retryAfterSeconds = refusal.retryAfterSeconds();
    }

    long retryAfterSeconds() {

        return retryAfterSeconds;
    }
}
