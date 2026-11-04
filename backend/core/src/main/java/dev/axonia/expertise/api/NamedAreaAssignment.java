package dev.axonia.expertise.api;

import java.util.List;

/** A named area after tags were assigned to it, and every tag that moved out of another named area to get there. */
public record NamedAreaAssignment(CuratedNamedArea area, List<Move> moved) {

    public NamedAreaAssignment {
        moved = List.copyOf(moved);
    }

    /** A tag that left the named area {@code fromId} for the one it was assigned to. */
    public record Move(String tag, long fromId, String fromName) {
    }
}
