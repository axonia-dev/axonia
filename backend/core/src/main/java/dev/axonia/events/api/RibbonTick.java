package dev.axonia.events.api;

import java.time.Instant;

/** One mark on the schedule's time scrubber. */
public record RibbonTick(Instant at, Kind kind) {

    /** Whether the mark is a past session, a planned one, or an occurrence a series projects. */
    public enum Kind {
        MEMORY, UPCOMING, PROJECTED
    }
}
