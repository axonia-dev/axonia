package dev.axonia.expertise.domain;

import java.util.List;

/**
 * A named area after tags were placed in it, and every tag that had to leave another named area to get there. A tag
 * sits in at most one named area, so placing one is a move whenever another area held it.
 */
public record NamedAreaPlacement(NamedArea area, List<Move> moves) {

    public NamedAreaPlacement {
        moves = List.copyOf(moves);
    }

    /** A tag that left the named area {@code fromId} for the one it was placed in. */
    public record Move(String tag, long fromId, String fromName) {
    }
}
