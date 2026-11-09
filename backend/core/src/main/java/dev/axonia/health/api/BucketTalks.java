package dev.axonia.health.api;

/**
 * The talks one presenter gave in one bucket of a window, dated by the session. The health core presence-filters these
 * and sums them into the talks series. The userId never leaves the core.
 */
public record BucketTalks(int bucket, String userId, long talks) {
}
