package dev.axonia.health;

/**
 * Aggregate reuse signal over a window, from the knowledge context. Demand = saves (self-saves excluded); applied value
 * = helped/notHelped answered prompts. Counts only — never who.
 */
public record ReuseCounts(long saves, long distinctArtifactsSaved, long helped, long notHelped) {
}
