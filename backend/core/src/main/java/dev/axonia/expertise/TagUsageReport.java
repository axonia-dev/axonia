package dev.axonia.expertise;

/**
 * What a tag is actually holding up, summed across every context that owns tagged content, plus the members who
 * declared it here, each counted once whatever spelling they used, and the members who want to learn it here under this
 * exact spelling. A curator sees this before confirming a delete, so the counts are what the dialog says out loud.
 */
public record TagUsageReport(int artifacts, int topics, int talks, int declarations, int learningIntents) {
}
