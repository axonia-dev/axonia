package dev.axonia.mentoring.adapter.out.communication;

import java.time.Clock;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.MessageSubjectPolicy;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import lombok.RequiredArgsConstructor;

/**
 * The mentoring notices' answers to the delivery gate, judged again at delivery. A proposal answered, withdrawn or
 * lapsed since is owed no notice, and its receiver must still be in the guild, and so is a reopening; a decline is owed
 * its "not this time" while the proposal stays declined by that answer, a next meeting's notice while that very setting
 * is the pairing's, and its reminder while that day is still the pairing's next meeting. A pairing notice is owed while
 * the pairing exists, to anyone still in the organization: a pairing belongs to its two people, not to the guild.
 * Asking for words is retired, so an ask still waiting in the outbox is owed nothing; panel entries still render.
 */
@Component("mentoringSubjectPolicy")
@RequiredArgsConstructor
class MentoringSubjectPolicy implements MessageSubjectPolicy {

    private final ProposalRepository proposals;
    private final PairingRepository pairings;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final Clock clock;

    @Override
    public Set<MessageType> handles() {

        return Set.of(MessageType.MENTORING_PROPOSAL, MessageType.MENTORING_DECLINED, MessageType.MENTORING_ENDED,
                MessageType.MENTORING_WORDS_ASKED, MessageType.MENTORING_WORDS_RECEIVED,
                MessageType.MENTORING_MEETING_SET, MessageType.MENTORING_MEETING_REMINDER,
                MessageType.MENTORING_PROPOSAL_REOPENED);
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
        if (MessageType.MENTORING_MEETING_SET.equals(type)) {
            return pairings.find(organizationId, subject.id()).filter(pairing -> isTheMeetingSet(pairing, subject))
                    .isPresent();
        }
        if (MessageType.MENTORING_MEETING_REMINDER.equals(type)) {
            return pairings.find(organizationId, subject.id()).filter(pairing -> isTheMeetingReminded(pairing, subject))
                    .isPresent();
        }
        return pairings.find(organizationId, subject.id()).isPresent();
    }

    /** A decline is owed while the proposal stays declined, by that very answer when the subject names it. */
    private static boolean isTheDecline(Proposal proposal, MentoringSubject subject) {

        return proposal.status() == ProposalStatus.DECLINED && (subject.occasion() == null
                || String.valueOf(proposal.answeredAt().toEpochMilli()).equals(subject.occasion()));
    }

    /** A reminder is owed while the pairing is ongoing and the reminded day is still its next meeting. */
    private static boolean isTheMeetingReminded(Pairing pairing, MentoringSubject subject) {

        return pairing.endedAt() == null && pairing.nextMeetingOn() != null
                && pairing.nextMeetingOn().toString().equals(subject.occasion());
    }

    /** A date set since, or cleared, or a pairing ended, supersedes the notice. */
    private static boolean isTheMeetingSet(Pairing pairing, MentoringSubject subject) {

        return pairing.endedAt() == null && pairing.nextMeetingSetAt() != null
                && String.valueOf(pairing.nextMeetingSetAt().toEpochMilli()).equals(subject.occasion());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStillAMember(String organizationId, String subjectRef, String userId) {

        var subject = MentoringSubject.parse(subjectRef);
        if (subject.kind() == MentoringSubject.Kind.PROPOSAL) {
            return proposals.find(organizationId, subject.id())
                    .map(proposal -> communityAccess.isMember(organizationId, proposal.communityId(), userId)
                            && personDirectory.isPresent(organizationId, userId))
                    .orElse(false);
        }
        return personDirectory.isPresent(organizationId, userId);
    }
}
