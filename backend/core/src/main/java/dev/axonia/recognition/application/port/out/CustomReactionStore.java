package dev.axonia.recognition.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.axonia.recognition.domain.CustomReaction;

/** Where an Organization's own reaction vocabulary is kept. Every list is ordered by name. */
public interface CustomReactionStore {

    List<CustomReaction> all(String organizationId);

    List<CustomReaction> inState(String organizationId, String state);

    List<CustomReaction> proposedBy(String organizationId, String state, String proposedByUserId);

    Optional<CustomReaction> find(String organizationId, Long id);

    boolean nameTaken(String organizationId, String name);

    int countInState(String organizationId, String state);

    int countProposedBy(String organizationId, String state, String proposedByUserId);

    /** How many placements ride on each definition, by definition id; a definition nobody placed may be absent. */
    Map<Long, Long> usageByDefinition(String organizationId);

    CustomReaction save(CustomReaction reaction);

    void remove(CustomReaction reaction);
}
