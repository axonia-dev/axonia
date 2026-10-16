package dev.axonia.events.api;

import java.time.Instant;

/** Where the next, older page of memories starts: the last session read, by instant and then by id. */
public record MemoryCursor(Instant at, long id) {
}
