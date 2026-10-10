package dev.axonia.health.api;

/**
 * How often a community's artifacts were followed out to, over a window. The top of the reuse funnel: reach, not
 * applied value. It says the knowledge was reached for, never that it helped - {@code helped} on the bookmark is the
 * only claim of that kind the product makes. Counts only, never who.
 */
public record OpeningCounts(long openings, long distinctArtifactsOpened) {
}
