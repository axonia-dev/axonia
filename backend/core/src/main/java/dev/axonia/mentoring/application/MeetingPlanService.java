package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.MeetingRequest;
import dev.axonia.mentoring.api.PlannedMeetingView;
import dev.axonia.mentoring.application.PairingParties.Party;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingNotice;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.MeetingPlan;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicBoard;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * The meetings a pairing plans. Either person plans, moves, re-topics or removes one, every rule read in their own
 * today and decided under the pairing's row lock. A meeting planned or moved tells the partner; a topic change or a
 * removal tells nobody, and a removal or a move takes back a notice still waiting, so the partner never learns of a
 * meeting already gone, hears of a meeting moved before its first notice left as planned, and of one moved again before
 * the last move's notice left as moved once.
 */
@Service
@RequiredArgsConstructor
public class MeetingPlanService {

    private final PairingParties parties;
    private final MeetingRepository meetings;
    private final JournalRepository journal;
    private final MemberClocks memberClocks;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final Clock clock;

    @Transactional
    public PlannedMeetingView plan(long pairingId, MeetingRequest request) {

        var party = parties.write(pairingId);
        var today = today(party);
        var day = MeetingPlan.plannable(request.on(), today);
        var board = journal.topicsOf(party.org(), pairingId);
        var topicIds = MeetingPlan.topics(request.topicIds(), ids(board));
        var planned = meetings.meetingsOf(party.org(), pairingId);
        MeetingPlan.requireNoNotes(journal.notesOf(party.org(), pairingId), day);
        MeetingPlan.requireFree(planned, day);
        MeetingPlan.requireRoom(planned, today);
        var meeting = PlannedMeeting.builder().pairingId(pairingId).on(day).topicIds(topicIds).plannedBy(party.me())
                .plannedAt(now()).build();
        meeting = meeting.toBuilder().id(insert(party.org(), meeting)).build();
        notice(party, meeting, board).ifPresent(notices::meetingPlanned);
        var plannedBy = personDirectory.resolve(List.of(party.me())).get(party.me());
        return PlannedMeetingView.builder().id(meeting.id()).on(day).topicIds(topicIds).plannedById(plannedBy.userId())
                .plannedByName(plannedBy.displayName()).passed(false).build();
    }

    @Transactional
    public void change(long pairingId, long meetingId, MeetingRequest request) {

        var party = parties.write(pairingId);
        var today = today(party);
        var meeting = meetingOf(party, meetingId);
        if (!MeetingPlan.isUpcoming(meeting, today)) {
            throw new ConflictException("MENTORING_MEETING_PASSED", "This meeting has passed.");
        }
        var day = MeetingPlan.plannable(request.on(), today);
        var board = journal.topicsOf(party.org(), pairingId);
        var changed = meeting.toBuilder().topicIds(MeetingPlan.topics(request.topicIds(), ids(board)));
        var moved = !day.equals(meeting.on());
        if (moved) {
            MeetingPlan.requireNoNotes(journal.notesOf(party.org(), pairingId), day);
            MeetingPlan.requireFree(meetings.meetingsOf(party.org(), pairingId), day);
            changed.on(day).plannedBy(party.me()).plannedAt(now());
        }
        var updated = changed.build();
        update(party.org(), updated);
        if (moved) {
            notice(party, updated, board).ifPresent(notice -> announceMove(party, meeting, notice));
        }
    }

    @Transactional
    public void remove(long pairingId, long meetingId) {

        var party = parties.write(pairingId);
        var meeting = meetingOf(party, meetingId);
        if (meeting.held()) {
            throw noSuchMeeting();
        }
        meetings.delete(party.org(), pairingId, meetingId);
        notices.withdrawMeetingPlanned(meeting);
        notices.withdrawMeetingMoved(meeting);
    }

    /** The unique day catches a write that raced past the check. */
    private long insert(String org, PlannedMeeting meeting) {

        try {
            return meetings.insert(org, meeting);
        } catch (DuplicateKeyException e) {
            throw MeetingPlan.taken();
        }
    }

    private void update(String org, PlannedMeeting meeting) {

        try {
            meetings.update(org, meeting);
        } catch (DuplicateKeyException e) {
            throw MeetingPlan.taken();
        }
    }

    private PlannedMeeting meetingOf(Party party, long meetingId) {

        return meetings.find(party.org(), meetingId).filter(m -> m.pairingId() == party.pairingId())
                .orElseThrow(MeetingPlanService::noSuchMeeting);
    }

    private static NotFoundException noSuchMeeting() {

        return new NotFoundException("No such meeting");
    }

    /**
     * A move of one's own meeting before its plan's notice left withdraws it and announces the plan anew, on its new
     * day. A move of the partner's meeting withdraws the partner's plan notice, which the mover would only read stale,
     * and tells the partner of the move. A move before an earlier move's notice left withdraws that one and stays a
     * move, since the partner already heard of the plan.
     */
    private void announceMove(Party party, PlannedMeeting before, MeetingNotice notice) {

        if (notices.withdrawMeetingPlanned(before) && party.me().equals(before.plannedBy())) {
            notices.meetingPlanned(notice);
        } else {
            notices.withdrawMeetingMoved(before);
            notices.meetingMoved(notice);
        }
    }

    /** What the partner is told of the meeting; nothing once the partner was erased. */
    private Optional<MeetingNotice> notice(Party party, PlannedMeeting meeting, List<Topic> board) {

        var pairing = party.pairing();
        var partner = MentoringViews.partnerOf(pairing, party.me());
        if (partner == null) {
            return Optional.empty();
        }
        var firstTopic = TopicBoard.trailOrder(board).stream().filter(t -> meeting.topicIds().contains(t.id()))
                .findFirst().map(Topic::label).orElse(null);
        return Optional.of(MeetingNotice.builder().pairing(pairing).meeting(meeting)
                .guild(communityAccess.guildOf(party.org(), pairing.communityId()))
                .byName(personDirectory.shortName(party.org(), party.me())).firstTopic(firstTopic).recipientId(partner)
                .build());
    }

    private LocalDate today(Party party) {

        return MeetingDays.dayOf(clock.instant(), memberClocks.zoneOf(party.org(), party.me()));
    }

    private Instant now() {

        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static Set<Long> ids(List<Topic> board) {

        return board.stream().map(Topic::id).collect(Collectors.toSet());
    }
}
