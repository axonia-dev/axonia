package dev.axonia.mentoring.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.axonia.mentoring.domain.PlannedMeeting;

public interface MeetingRepository {

    /** A second meeting of the pairing on the same day raises {@code DuplicateKeyException}. */
    long insert(String org, PlannedMeeting meeting);

    /** Replaces the day, who planned it, when, and its topics. */
    void update(String org, PlannedMeeting meeting);

    void delete(String org, long pairingId, long meetingId);

    Optional<PlannedMeeting> find(String org, long meetingId);

    /** Day ascending, held ones included. */
    List<PlannedMeeting> meetingsOf(String org, long pairingId);

    /** Each pairing's meetings, day ascending; a pairing with none is absent. */
    Map<Long, List<PlannedMeeting>> meetingsOf(String org, Collection<Long> pairingIds);

    /** Meetings of ongoing pairings between the days, both included, that no note has held. */
    List<PlannedMeeting> notHeldBetween(String org, LocalDate from, LocalDate to);

    /** Removes the pairing's meetings on {@code today} or later that are not held. */
    void deleteUpcoming(String org, long pairingId, LocalDate today);
}
