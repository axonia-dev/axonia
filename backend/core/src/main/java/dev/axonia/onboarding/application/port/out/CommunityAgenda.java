package dev.axonia.onboarding.application.port.out;

import java.util.Optional;

import dev.axonia.onboarding.domain.Pick;

/** What a community last did, what it does next, and what it is currently weighing up. */
public interface CommunityAgenda {

    Optional<Pick> lastHeld(String slug);

    Optional<Pick> nextUp(String slug);

    Optional<Pick> topVotedTopic(String slug);
}
