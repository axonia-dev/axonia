package dev.axonia.recognition.domain;

import dev.axonia.shared.api.ratelimit.RateLimitDecision;

/**
 * The caller has spent their reaction-proposal budget for this window. Carries the wait so the 429 can be honest about
 * it.
 */
public class ReactionRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public ReactionRateLimitedException(RateLimitDecision refusal) {

        super("Too many reaction proposals. Try again shortly.");
        this.retryAfterSeconds = refusal.retryAfterSeconds();
    }

    public long retryAfterSeconds() {

        return retryAfterSeconds;
    }
}
