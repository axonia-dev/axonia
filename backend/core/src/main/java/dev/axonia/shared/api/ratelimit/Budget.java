package dev.axonia.shared.api.ratelimit;

import java.time.Duration;

/**
 * How much one caller may spend before the product starts refusing them: {@code limit} attempts per {@code window}.
 *
 * <p>
 * The product's own word for this: an abuse budget.
 *
 * <p>
 * Pairing the two also keeps {@link RateLimiter}'s constructor at four parameters. PMD's {@code ExcessiveParameterList}
 * flags five, and this project shrinks signatures rather than suppressing the finding.
 */
public record Budget(int limit, Duration window) {

    public Budget {
        if (limit < 1) {
            throw new IllegalArgumentException("a budget of %d would refuse every caller forever".formatted(limit));
        }
    }
}
