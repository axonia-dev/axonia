package dev.axonia.community.api;

import java.util.List;

/** The first page of one shelf, and whether further pages exist. */
public record LobbyShelf(LobbyShelfKey key, List<LobbyItem> items, boolean hasMore) {

    public LobbyShelf {
        items = List.copyOf(items);
    }
}
