package dev.axonia.events.api;

/** What a past session left behind: the loves its talks and artifacts drew, a recording, and its artifacts. */
public record MemoryTraces(long loves, boolean recorded, long artifacts) {
}
