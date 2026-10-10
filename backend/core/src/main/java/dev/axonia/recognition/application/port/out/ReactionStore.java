package dev.axonia.recognition.application.port.out;

import java.util.List;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.domain.ReactionPlacement;
import dev.axonia.recognition.domain.ReactionPlacementKey;

/** Where placed reactions are kept. */
public interface ReactionStore {

    List<ReactionPlacement> placementsOn(String organizationId, ContributionType contributionType, Long contributionId);

    /** Stores a new placement; {@code false} when the very same reaction is already given. */
    boolean place(ReactionPlacement placement);

    /** Removes the placement the key names, answering how many were removed. */
    int remove(ReactionPlacementKey key);
}
