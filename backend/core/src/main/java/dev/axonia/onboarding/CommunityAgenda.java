package dev.axonia.onboarding;

import java.util.Optional;

/** What a community last did, what it does next, and what it is currently weighing up. */
public interface CommunityAgenda {

    Optional<Pick> lastHeld(String slug);

    Optional<Pick> nextUp(String slug);

    Optional<Pick> topVotedTopic(String slug);
}
