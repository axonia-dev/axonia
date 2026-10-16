package dev.axonia.events.api;

/** A past session as the timeline remembers it: the schedule entry it was, and the traces it left. */
public record Memory(ScheduleEntry entry, MemoryTraces traces) {
}
