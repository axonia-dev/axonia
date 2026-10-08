package dev.axonia.recognition;

import dev.axonia.RateLimitDecision;

/**
 * The caller has spent their reaction-proposal budget for this window. Carries the wait so the 429 can be honest about
 * it.
 */
class ReactionRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    ReactionRateLimitedException(RateLimitDecision refusal) {

        super("Too many reaction proposals. Try again shortly.");
        this.retryAfterSeconds = refusal.retryAfterSeconds();
    }

    long retryAfterSeconds() {

        return retryAfterSeconds;
    }
}
