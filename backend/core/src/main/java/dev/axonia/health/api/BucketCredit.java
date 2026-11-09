package dev.axonia.health.api;

/**
 * One person credited with at least one act in one bucket of a window. One row per distinct user per bucket per act
 * source; the health core presence-filters these before counting, so the per-bucket counts describe only people who are
 * still here. The userId never leaves the core.
 */
public record BucketCredit(int bucket, String userId) {
}
