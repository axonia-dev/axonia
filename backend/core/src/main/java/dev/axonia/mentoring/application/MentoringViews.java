package dev.axonia.mentoring.application;

import java.time.LocalDate;
import java.util.Map;

import dev.axonia.mentoring.api.NextMeetingDay;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;

/** Proposals and pairings as one of their two members reads them. */
final class MentoringViews {

    private static final String LAPSED = "LAPSED";

    private MentoringViews() {
    }

    /** Total: an erased side, whose id is null, reads as a deleted member with no id and no avatar. */
    static MentoringPerson person(Map<String, MentoringPerson> people, String userId) {

        return userId == null ? MentoringPerson.deleted(null) : people.get(userId);
    }

    static String partnerOf(Pairing pairing, String me) {

        return me.equals(pairing.mentorUserId()) ? pairing.menteeUserId() : pairing.mentorUserId();
    }

    /** The proposal as one of its two people reads it; whether it may be reopened is My mentoring's to add. */
    static ProposalView proposal(Proposal proposal, MentoringCommunity community, MentoringPerson partner,
            CallerTime at) {

        var now = at.now();
        return ProposalView.builder().id(proposal.id()).communitySlug(community.slug()).communityName(community.name())
                .proposedBy(proposal.proposedBy().name()).partnerId(partner.userId()).partnerName(partner.displayName())
                .partnerAvatarUrl(partner.avatarUrl()).topics(proposal.topics()).message(proposal.message())
                .status(MentoringWindows.isLapsed(proposal, now) ? LAPSED : proposal.status().name())
                .createdAt(proposal.createdAt()).lapsesOn(lapsesOn(proposal, at)).build();
    }

    /** The caller's day on which an open proposal lapses, counted from its last opening; null once it is closed. */
    private static LocalDate lapsesOn(Proposal proposal, CallerTime at) {

        return MentoringWindows.isOpen(proposal, at.now())
                ? MeetingDays.dayOf(MentoringWindows.openSince(proposal).plus(MentoringWindows.PROPOSAL_LIFETIME),
                        at.zone())
                : null;
    }

    /** The pairing as {@code me} reads it, short of its {@link #glance}. */
    static PairingSummary.PairingSummaryBuilder pairing(Pairing pairing, MentoringCommunity community, String me,
            MentoringPerson partner) {

        var myRole = me.equals(pairing.mentorUserId()) ? Role.MENTOR : Role.MENTEE;
        return PairingSummary.builder().id(pairing.id()).communitySlug(community.slug()).communityName(community.name())
                .myRole(myRole.name()).partnerId(partner.userId()).partnerName(partner.displayName())
                .partnerAvatarUrl(partner.avatarUrl()).startedAt(pairing.startedAt()).endedAt(pairing.endedAt());
    }

    /** What a glance at the pairing tells: its stops, the topic in progress, the next meeting and the notes counted. */
    static PairingSummary.PairingSummaryBuilder glance(PairingSummary.PairingSummaryBuilder builder, Pairing pairing,
            PairingProgress progress, LocalDate today) {

        var trail = progress.trail();
        var tally = progress.tally();
        return builder.stops(trail.stream().map(t -> t.state().name()).toList())
                .nowTopic(trail.stream().filter(t -> t.state() == TopicState.IN_PROGRESS).findFirst().map(Topic::label)
                        .orElse(null))
                .nextMeeting(nextMeeting(pairing, today)).meetings(tally.meetings()).notes(tally.notes())
                .lastMetOn(tally.lastMetOn());
    }

    private static NextMeetingDay nextMeeting(Pairing pairing, LocalDate today) {

        var on = pairing.nextMeetingOn();
        return on == null || pairing.endedAt() != null ? null : new NextMeetingDay(on, on.isBefore(today));
    }
}
