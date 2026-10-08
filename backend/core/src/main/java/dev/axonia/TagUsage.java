package dev.axonia;

/** How much content of one kind carries a tag. */
public record TagUsage(TagUsageKind kind, int count) {
}
