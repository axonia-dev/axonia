package dev.axonia.community.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;

/**
 * A community as the lobby shows it: its listing, plus what is alive inside it. {@code faces} are member user ids, the
 * newest join first.
 */
@Builder
public record LobbyItem(@JsonUnwrapped CommunityListing listing, LobbySession nextSession, boolean nextSessionThisWeek,
        LobbySession latestSession, long joinedLast30Days, List<String> faces, LobbyStats stats, LobbyLately lately) {

    public LobbyItem {
        faces = List.copyOf(faces);
    }
}
