package dev.axonia.mentoring.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Showing;

public interface PairingRepository {

    /** A second ongoing pairing of the same two members in a community raises {@code DuplicateKeyException}. */
    long insert(String org, Pairing pairing);

    Optional<Pairing> find(String org, long id);

    /**
     * The pairing, row-locked until the transaction ends, so journal writes are decided one at a time and an end waits
     * for the write in flight.
     */
    Optional<Pairing> findForWrite(String org, long id);

    /** Newest first. */
    List<Pairing> involving(String org, String userId);

    /** The user's ongoing pairings in one community, or in every community when {@code communityId} is null. */
    List<Pairing> ongoingInvolving(String org, Long communityId, String userId);

    int ongoingAsMentor(String org, long communityId, String mentorId);

    /** Each mentor's count of ongoing pairings in the community; a mentor with none is absent. */
    Map<String, Integer> ongoingAsMentorIn(String org, long communityId);

    /** Whether the user is, or was, on any pairing or any proposal in the organization. */
    boolean anyMentoringInvolving(String org, String userId);

    /** The community's ongoing, shown pairings with both sides present, in no particular order. */
    List<Pairing> shownIn(String org, long communityId);

    void saveShowing(String org, long pairingId, Showing showing);

    /** Ends the pairing only while it is still ongoing; false when it had already ended. */
    boolean end(String org, long id, EndReason reason, Instant at);

    /** Transaction-scoped lock serialising everything that pairs, or unpairs, one member in one community. */
    void lockMember(String org, long communityId, String userId);
}
