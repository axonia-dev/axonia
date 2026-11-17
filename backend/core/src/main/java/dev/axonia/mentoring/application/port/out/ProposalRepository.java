package dev.axonia.mentoring.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;

public interface ProposalRepository {

    long insert(String org, Proposal proposal);

    Optional<Proposal> find(String org, long id);

    /** Every proposal between the two people in the community, in either direction and either role, newest first. */
    List<Proposal> between(String org, long communityId, String one, String other);

    /** Newest first. */
    List<Proposal> involving(String org, String userId);

    /** Every proposal the user is on in one community, either way and in either role, newest first. */
    List<Proposal> involvingIn(String org, long communityId, String userId);

    /** Answers the proposal only while it is still open; false when somebody else answered it first. */
    boolean answer(String org, long id, ProposalStatus status, Instant at);

    /** Opens a declined proposal again, stamping {@code reopenedAt}; false when it is not declined any more. */
    boolean reopen(String org, long id, Instant at);

    /**
     * Withdraws every other open proposal between the two members of {@code accepted} in its community, whichever way
     * it was sent.
     */
    void withdrawOthersBetween(String org, Proposal accepted, Instant at);

    /** Withdraws the user's open proposals in one community, or in every community when {@code communityId} is null. */
    void withdrawOpenInvolving(String org, Long communityId, String userId, Instant at);
}
