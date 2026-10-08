package dev.axonia.recognition;

import lombok.Builder;

/**
 * What the picker's footer needs: whether to draw "Add a reaction" at all, and how much room is left. Both ceilings
 * travel with the answer so no screen has to keep its own copy of a number this service owns.
 */
@Builder
public record ReactionEligibility(boolean mayPropose, int openProposals, int openProposalLimit, int approvedLimit) {
}
