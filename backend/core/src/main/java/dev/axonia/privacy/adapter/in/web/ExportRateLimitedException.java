package dev.axonia.privacy.adapter.in.web;

import dev.axonia.shared.api.ratelimit.RateLimitDecision;

/**
 * Thrown by {@link ExportDownload} when a caller has exceeded its export budget. Mapped to {@code 429} by
 * {@link PrivacyExceptionHandler} rather than left to fall through as a plain 500: an export reads an entire
 * Organization, so being throttled is an expected, retryable outcome, not a server failure.
 *
 * <p>
 * It carries the {@code Retry-After} value already in seconds rather than the raw {@link java.time.Duration}, so the
 * floor-and-round-up rule lives only in {@link RateLimitDecision#retryAfterSeconds()} and the handler cannot re-derive
 * a different one.
 */
class ExportRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    ExportRateLimitedException(RateLimitDecision refusal) {

        super("Too many export requests. Try again shortly.");
        this.retryAfterSeconds = refusal.retryAfterSeconds();
    }

    public long retryAfterSeconds() {

        return retryAfterSeconds;
    }
}
