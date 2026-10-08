package dev.axonia.expertise;

import java.util.List;

/**
 * A grouping of canonical tags a leader named. Unlike a neighbourhood, which the map derives on every render, it is
 * stored and only changes when a person changes it.
 */
public record NamedArea(long id, String name, List<String> tags) {

    public NamedArea {
        tags = List.copyOf(tags);
    }
}
