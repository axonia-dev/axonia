package dev.axonia.mentoring.adapter.out.communication;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.MeetingPlan;
import dev.axonia.mentoring.domain.MentorListing;
import dev.axonia.mentoring.domain.MentorRoster;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import lombok.RequiredArgsConstructor;

/**
 * The mentoring notices' answers to the delivery gate, judged again at delivery. A proposal answered, withdrawn or
 * lapsed since is owed no notice, and its receiver must still be in the guild, and so is a reopening; a decline is owed
 * its "not this time" while the proposal stays declined by that answer, a planned or moved meeting's notice while that
 * very planning stands, its meeting is upcoming (unheld, its day not before today in UTC) and its pairing ongoing, and
 * a reminder while its meeting stays unheld on that day by that planning, in an ongoing pairing. A meeting notice or
 * reminder from before meetings were planned one by one names the pairing, and is owed nothing. A pairing notice is
 * owed while the pairing exists, to anyone still in the organization: a pairing belongs to its two people, not to the
 * guild. Asking for words is retired, so an ask still waiting in the outbox is owed nothing; panel entries still
 * render. A listing ask is owed while the mentor still has it to answer, and a told notice while the mentor is still
 * listed; both only to a mentor still in the guild.
 */
@Component("mentoringSubjectPolicy")
@RequiredArgsConstructor
class MentoringSubjectPolicy implements MessageSubjectPolicy {

    private final ProposalRepository proposals;
    private final PairingRepository pairings;
    private final MeetingRepository meetings;
    private final MentorListingRepository listings;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final Clock clock;

    @Override
    public Set<MessageType> handles() {

        return Set.of(MessageType.MENTORING_PROPOSAL, MessageType.MENTORING_DECLINED, MessageType.MENTORING_ENDED,
                MessageType.MENTORING_WORDS_ASKED, MessageType.MENTORING_WORDS_RECEIVED,
                MessageType.MENTORING_MEETING_SET, MessageType.MENTORING_MEETING_REMINDER,
                MessageType.MENTORING_PROPOSAL_REOPENED, MessageType.MENTORING_LISTING_ASK,
                MessageType.MENTORING_LISTED);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabledFor(String organizationId, MessageType type, String subjectRef) {

        if (MessageType.MENTORING_WORDS_ASKED.equals(type)) {
            return false;
        }
        var subject = MentoringSubject.parse(subjectRef);
        if (MessageType.MENTORING_DECLINED.equals(type)) {
            return proposals.find(organizationId, subject.id()).filter(proposal -> isTheDecline(proposal, subject))
                    .isPresent();
        }
        if (subject.kind() == MentoringSubject.Kind.PROPOSAL) {
            return proposals.find(organizationId, subject.id())
                    .filter(proposal -> MentoringWindows.isOpen(proposal, clock.instant())).isPresent();
        }
        if (subject.kind() == MentoringSubject.Kind.LISTING) {
            return isOwedOfTheListing(organizationId, type, subject);
        }
        if (MessageType.MENTORING_MEETING_SET.equals(type)) {
            var today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            return isOwedOfAnOngoingPairing(organizationId, subject, meeting -> MeetingPlan.isUpcoming(meeting, today)
                    && MeetingNoticeKind.namesThePlanning(meeting, subject.occasion()));
        }
        if (MessageType.MENTORING_MEETING_REMINDER.equals(type)) {
            return isOwedOfAnOngoingPairing(organizationId, subject, meeting -> !meeting.held()
                    && MentoringNoticesAdapter.reminderOccasion(meeting).equals(subject.occasion()));
        }
        return pairings.find(organizationId, subject.id()).isPresent();
    }

    /** A decline is owed while the proposal stays declined, by that very answer when the subject names it. */
    private static boolean isTheDecline(Proposal proposal, MentoringSubject subject) {

        return proposal.status() == ProposalStatus.DECLINED && (subject.occasion() == null
                || String.valueOf(proposal.answeredAt().toEpochMilli()).equals(subject.occasion()));
    }

    /** The ask while the mentor still has it to answer, the told notice while the mentor is still listed. */
    private boolean isOwedOfTheListing(String organizationId, MessageType type, MentoringSubject subject) {

        var mentorId = subject.aboutUserId();
        var history = pairings.mentorHistoriesIn(organizationId, subject.id()).stream()
                .filter(h -> h.mentorId().equals(mentorId)).findFirst();
        if (history.isEmpty()) {
            return false;
        }
        var state = MentorListing.switchOf(listings.in(organizationId, subject.id()).get(mentorId));
        return MessageType.MENTORING_LISTING_ASK.equals(type)
                ? MentorRoster.needsAsk(history.get(), state)
                : MentorRoster.status(history.get(), state).isPresent();
    }

    /** A meeting notice is owed while its meeting stands as the subject names it and its pairing is ongoing. */
    private boolean isOwedOfAnOngoingPairing(String organizationId, MentoringSubject subject,
            Predicate<PlannedMeeting> standsAsNamed) {

        return subject.kind() == MentoringSubject.Kind.MEETING && meetings.find(organizationId, subject.id())
                .filter(standsAsNamed).flatMap(meeting -> pairings.find(organizationId, meeting.pairingId()))
                .filter(pairing -> pairing.endedAt() == null).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStillAMember(String organizationId, String subjectRef, String userId) {

        var subject = MentoringSubject.parse(subjectRef);
        if (subject.kind() == MentoringSubject.Kind.LISTING) {
            return communityAccess.isMember(organizationId, subject.id(), userId)
                    && personDirectory.isPresent(organizationId, userId);
        }
        if (subject.kind() == MentoringSubject.Kind.PROPOSAL) {
            return proposals.find(organizationId, subject.id())
                    .map(proposal -> communityAccess.isMember(organizationId, proposal.communityId(), userId)
                            && personDirectory.isPresent(organizationId, userId))
                    .orElse(false);
        }
        return personDirectory.isPresent(organizationId, userId);
    }
}
