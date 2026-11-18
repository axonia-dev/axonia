package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.MentoringPresence;
import dev.axonia.mentoring.api.MyMentoring;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.PairHold;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.RequiredArgsConstructor;

/**
 * How a pairing ends, by choice, by leaving the guild or by deactivation, and what My mentoring lists. A pairing
 * belongs to its two people: reading it and ending it never ask whether either is still in the guild.
 */
@Service
@RequiredArgsConstructor
public class PairingService {

    private static final String NOT_FOUND = "No such pairing";

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final PairingRepository pairings;
    private final ProposalRepository proposals;
    private final JournalRepository journal;
    private final MeetingRepository meetings;
    private final AvailabilityRepository availabilities;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final MemberClocks memberClocks;
    private final Clock clock;

    @Transactional
    public void end(long pairingId) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var pairing = pairings.find(org, pairingId)
                .filter(p -> me.equals(p.mentorUserId()) || me.equals(p.menteeUserId()))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        var reason = me.equals(pairing.mentorUserId()) ? EndReason.MENTOR : EndReason.MENTEE;
        if (pairing.endedAt() != null || !endAndTell(org, pairing, reason, me)) {
            throw new ConflictException("MENTORING_PAIRING_ENDED", "This pairing has already ended");
        }
    }

    @Transactional(readOnly = true)
    public MyMentoring mine() {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        if (!personDirectory.isPresent(org, me)) {
            throw new ForbiddenException("Only members of the organization read their mentoring");
        }
        var mine = pairings.involving(org, me);
        var involved = proposals.involving(org, me);
        var people = personDirectory
                .resolve(Stream
                        .concat(mine.stream().map(p -> MentoringViews.partnerOf(p, me)),
                                involved.stream().map(p -> partnerOf(p, me)))
                        .filter(Objects::nonNull).distinct().toList());
        var communities = new HashMap<Long, MentoringCommunity>();
        Function<Long, MentoringCommunity> community = id -> communities.computeIfAbsent(id,
                communityAccess::communityById);
        var at = new CallerTime(clock.instant(), memberClocks.zoneOf(org, me));
        var ids = mine.stream().map(Pairing::id).toList();
        var trails = journal.trailsOf(org, ids);
        var tallies = journal.tallyOf(org, ids);
        var plans = meetings.meetingsOf(org, mine.stream().filter(p -> p.endedAt() == null).map(Pairing::id).toList());
        var summaries = mine.stream()
                .map(p -> MentoringViews.glance(
                        MentoringViews.pairing(p, community.apply(p.communityId()), me,
                                MentoringViews.person(people, MentoringViews.partnerOf(p, me))),
                        p,
                        new PairingProgress(trails.getOrDefault(p.id(), List.of()),
                                tallies.getOrDefault(p.id(), NoteTally.NONE), plans.getOrDefault(p.id(), List.of())),
                        at.today()).build())
                .toList();
        Function<Proposal, ProposalView> view = p -> MentoringViews.proposal(p, community.apply(p.communityId()),
                MentoringViews.person(people, partnerOf(p, me)), at);
        var history = new History(me, mine, involved, at.now());
        return MyMentoring.builder().today(at.today()).inAMentoringGuild(communityAccess.inAMentoringGuild(org, me))
                .received(involved.stream().filter(p -> me.equals(p.receiverId()))
                        .map(p -> view.apply(p).toBuilder().reopenableUntil(history.reopenableUntil(p)).build())
                        .toList())
                .sent(involved.stream().filter(p -> me.equals(p.senderId())).map(view).toList())
                .ongoing(summaries.stream().filter(s -> s.endedAt() == null).toList())
                .ended(summaries.stream().filter(s -> s.endedAt() != null)
                        .sorted(Comparator.comparing(PairingSummary::endedAt).reversed()).toList())
                .build();
    }

    /** The shell's probe: one query when the member sits in a mentoring guild, two when they do not. */
    @Transactional(readOnly = true)
    public MentoringPresence presence() {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        return new MentoringPresence(
                communityAccess.inAMentoringGuild(org, me) || pairings.anyMentoringInvolving(org, me));
    }

    /** Runs off any request, inside the leave transaction: everything is read for the event's organization. */
    @Transactional
    public void departedFromGuild(String org, long communityId, String userId) {

        pairings.lockMember(org, communityId, userId);
        pairings.ongoingInvolving(org, communityId, userId).forEach(p -> endAndTell(org, p, EndReason.LEFT, userId));
        proposals.withdrawOpenInvolving(org, communityId, userId, now());
        availabilities.deleteIn(org, communityId, userId);
    }

    /**
     * Runs off any request, inside the deactivating transaction. The person's lock in each of their guilds is taken
     * first, in id order, so an acceptance in flight either commits before the pairings are read or reads the person
     * gone.
     */
    @Transactional
    public void deactivated(String org, String userId) {

        communityAccess.communityIdsOf(org, userId).stream().sorted()
                .forEach(communityId -> pairings.lockMember(org, communityId, userId));
        pairings.ongoingInvolving(org, null, userId).forEach(p -> endAndTell(org, p, EndReason.DEACTIVATED, userId));
        proposals.withdrawOpenInvolving(org, null, userId, now());
    }

    /**
     * False, and nobody is told, when somebody else ended the pairing first. Its upcoming meetings go, read in the day
     * of whoever ended it, left or was deactivated; its passed and held ones stay.
     */
    private boolean endAndTell(String org, Pairing pairing, EndReason reason, String endedBy) {

        if (!pairings.end(org, pairing.id(), reason, now())) {
            return false;
        }
        meetings.deleteUpcoming(org, pairing.id(),
                MeetingDays.dayOf(clock.instant(), memberClocks.zoneOf(org, endedBy)));
        var other = MentoringViews.partnerOf(pairing, endedBy);
        if (other != null) {
            notices.ended(pairing, communityAccess.guildOf(org, pairing.communityId()),
                    personDirectory.shortName(org, endedBy), other);
        }
        return true;
    }

    private static String partnerOf(Proposal proposal, String me) {

        return me.equals(proposal.senderId()) ? proposal.receiverId() : proposal.senderId();
    }

    private Instant now() {

        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Everything the caller has had with anyone, read once, to tell which declines they may still reopen. */
    private record History(String me, List<Pairing> pairings, List<Proposal> proposals, Instant now) {

        /** Null unless the caller declined it and may still reopen it, the rule reopening itself applies. */
        Instant reopenableUntil(Proposal declined) {

            var other = partnerOf(declined, me);
            if (other == null) {
                return null;
            }
            var between = proposals.stream()
                    .filter(p -> p.communityId() == declined.communityId() && other.equals(partnerOf(p, me))).toList();
            var pairingsOfTheTwo = pairings.stream().filter(
                    p -> p.communityId() == declined.communityId() && other.equals(MentoringViews.partnerOf(p, me)))
                    .toList();
            return PairHold.isReopenable(declined, between, pairingsOfTheTwo, now)
                    ? MentoringWindows.reopenableUntil(declined)
                    : null;
        }
    }
}
