package dev.axonia.community.api;

import java.util.List;

public record Lobby(List<LobbyItem> billboard, List<LobbyShelf> shelves) {

    public Lobby {
        billboard = List.copyOf(billboard);
        shelves = List.copyOf(shelves);
    }
}
