package dev.axonia.expertise.api;

import java.util.List;

/** A named grouping of a community's canonical tags. A tag belongs to at most one named area. */
public record CuratedNamedArea(long id, String name, List<String> tags) {

    public CuratedNamedArea {
        tags = List.copyOf(tags);
    }
}
