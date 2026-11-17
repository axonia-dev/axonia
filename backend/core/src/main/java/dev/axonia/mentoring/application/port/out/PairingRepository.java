package dev.axonia.mentoring.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.NextMeeting;
import dev.axonia.mentoring.domain.Pairing;

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

    /** Ends the pairing only while it is still ongoing, clearing its next meeting; false when it had already ended. */
    boolean end(String org, long id, EndReason reason, Instant at);

    void setNextMeeting(String org, long id, NextMeeting next);

    void clearNextMeeting(String org, long id);

    /** The ongoing pairings whose next meeting falls from {@code from} to {@code to}, both included. */
    List<Pairing> withNextMeetingBetween(String org, LocalDate from, LocalDate to);

    /** Transaction-scoped lock serialising everything that pairs, or unpairs, one member in one community. */
    void lockMember(String org, long communityId, String userId);
}
