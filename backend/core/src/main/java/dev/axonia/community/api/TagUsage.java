package dev.axonia.community.api;

/** How much content of one kind carries a tag. */
public record TagUsage(TagUsageKind kind, int count) {
}
