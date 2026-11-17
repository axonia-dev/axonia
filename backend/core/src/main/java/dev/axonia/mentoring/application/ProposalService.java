package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.api.ProposeRequest;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.PairHold;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalRules;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * A proposal, from either side, and the pairing its acceptance creates. Proposing, accepting and reopening all hold the
 * two members' locks in id order, so a double submit, two crossed proposals or a mentor's last free slot are decided
 * one at a time. The locks are the guard: the ongoing-pair index only catches the same two in the same roles.
 */
@Service
@RequiredArgsConstructor
public class ProposalService {

    private static final String NOT_FOUND = "No such proposal";

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final ProposalRepository proposals;
    private final PairingRepository pairings;
    private final PairingGuards guards;
    private final FirstTopics firstTopics;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final MemberClocks memberClocks;
    private final Clock clock;

    @Transactional
    public ProposalView propose(String slug, ProposeRequest request) {

        requireOwnSignIn();
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var community = communityAccess.communityForSlug(slug);
        if (!communityAccess.isMember(community.id(), me)) {
            throw new ForbiddenException("Only members propose mentoring");
        }
        community.requireTakingProposals();
        var asRole = roleOf(request.asRole());
        var to = request.toUserId();
        ProposalRules.requireAnotherPerson(me, to);
        var topics = ProposalRules.topics(request.topics());
        var message = ProposalRules.message(request.message());
        lockBoth(org, community.id(), me, to);
        guards.requireListed(org, community.id(), to, asRole);
        guards.requireNotPaired(org, community.id(), me, to);
        var now = now();
        var draft = Proposal.builder().communityId(community.id()).mentorUserId(asRole == Role.MENTOR ? me : to)
                .menteeUserId(asRole == Role.MENTOR ? to : me).proposedBy(asRole).topics(topics).message(message)
                .status(ProposalStatus.OPEN).createdAt(now);
        PairHold.of(proposals.between(org, community.id(), me, to), me, now).requireMayPropose();
        var unsaved = draft.build();
        var proposal = draft.id(proposals.insert(org, unsaved)).build();
        var people = personDirectory.resolve(List.of(me, to));
        notices.proposed(org, proposal, MentoringGuild.of(community), people.get(me).displayName());
        return MentoringViews.proposal(proposal, community, people.get(to),
                new CallerTime(now, memberClocks.zoneOf(org, me)));
    }

    @Transactional
    public PairingSummary accept(long proposalId) {

        requireOwnSignIn();
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var received = proposals.find(org, proposalId).filter(p -> me.equals(p.receiverId()))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        var community = communityAccess.communityById(received.communityId());
        community.requireTakingProposals();
        firstTopics.lockVocabulary(org, community);
        lockBoth(org, community.id(), received.mentorUserId(), received.menteeUserId());
        var proposal = proposals.find(org, proposalId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        var now = now();
        var sender = proposal.senderId();
        if (!MentoringWindows.isOpen(proposal, now) || !bothStillHere(org, community.id(), me, sender)) {
            throw closed();
        }
        guards.requireNotPaired(org, community.id(), me, sender);
        guards.requireRoom(org, community.id(), proposal.mentorUserId());
        if (!proposals.answer(org, proposalId, ProposalStatus.ACCEPTED, now)) {
            throw closed();
        }
        var draft = Pairing.builder().communityId(community.id()).mentorUserId(proposal.mentorUserId())
                .menteeUserId(proposal.menteeUserId()).startedAt(now);
        var pairing = draft.id(insert(org, draft.build())).build();
        proposals.withdrawOthersBetween(org, proposal, now);
        var trail = firstTopics.seed(org, community, pairing, proposal.topics());
        return MentoringViews.glance(
                MentoringViews.pairing(pairing, community, me, personDirectory.resolve(List.of(sender)).get(sender)),
                pairing, new PairingProgress(trail, NoteTally.NONE),
                new CallerTime(now, memberClocks.zoneOf(org, me)).today()).build();
    }

    /** The receiver's "not this time": no reason, and the sender is told just that. */
    @Transactional
    public void decline(long proposalId) {

        var proposal = close(proposalId, true, ProposalStatus.DECLINED);
        var org = organizationContext.organizationId();
        notices.declined(proposal, communityAccess.guildOf(org, proposal.communityId()),
                personDirectory.shortName(org, proposal.receiverId()));
    }

    /**
     * The person who declined opens the proposal again, within the decline's wait, for a fresh lifetime; the sender is
     * told, unless the decline was still waiting to reach them: it is withdrawn instead, and nothing changed for them.
     * It passes what proposing passes, but the decline's own wait. Accepting stays a separate step with its own checks,
     * such as the mentor's room.
     */
    @Transactional
    public void reopen(long proposalId) {

        requireOwnSignIn();
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var received = proposals.find(org, proposalId).filter(p -> me.equals(p.receiverId()))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        var community = communityAccess.communityById(received.communityId());
        community.requireTakingProposals();
        lockBoth(org, community.id(), received.mentorUserId(), received.menteeUserId());
        var proposal = proposals.find(org, proposalId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        var now = now();
        var sender = proposal.senderId();
        if (!MentoringWindows.isDeclinedRecently(proposal, now) || !bothStillHere(org, community.id(), me, sender)) {
            throw closed();
        }
        guards.requireNotPaired(org, community.id(), me, sender);
        var between = proposals.between(org, community.id(), me, sender);
        PairHold.of(between, me, now).requireNoneOpen();
        var pairingsOfTheTwo = pairings.involving(org, me).stream()
                .filter(p -> p.communityId() == community.id() && sender.equals(MentoringViews.partnerOf(p, me)))
                .toList();
        if (!PairHold.isReopenable(proposal, between, pairingsOfTheTwo, now)
                || !proposals.reopen(org, proposalId, now)) {
            throw closed();
        }
        if (!notices.withdrawDeclined(proposal)) {
            notices.reopened(proposal.toBuilder().status(ProposalStatus.OPEN).answeredAt(null).reopenedAt(now).build(),
                    MentoringGuild.of(community), personDirectory.shortName(org, me));
        }
    }

    @Transactional
    public void withdraw(long proposalId) {

        close(proposalId, false, ProposalStatus.WITHDRAWN);
    }

    private Proposal close(long proposalId, boolean byReceiver, ProposalStatus status) {

        requireOwnSignIn();
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var proposal = proposals.find(org, proposalId)
                .filter(p -> me.equals(byReceiver ? p.receiverId() : p.senderId()))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        var now = now();
        if (!MentoringWindows.isOpen(proposal, now) || !proposals.answer(org, proposalId, status, now)) {
            throw closed();
        }
        return proposal.toBuilder().status(status).answeredAt(now).build();
    }

    /** Both people's locks in id order, so two callers locking the same pair never wait on each other in a cycle. */
    private void lockBoth(String org, long communityId, String one, String other) {

        Stream.of(one, other).sorted().forEach(id -> pairings.lockMember(org, communityId, id));
    }

    private boolean bothStillHere(String org, long communityId, String me, String sender) {

        return communityAccess.isMember(communityId, me) && communityAccess.isMember(communityId, sender)
                && personDirectory.isPresent(org, me) && personDirectory.isPresent(org, sender);
    }

    private void requireOwnSignIn() {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
    }

    private static Role roleOf(String raw) {

        return Stream.of(Role.values()).filter(role -> role.name().equals(raw)).findFirst()
                .orElseThrow(() -> new ValidationException("field_invalid", "A proposer proposes as MENTOR or MENTEE"));
    }

    private long insert(String org, Pairing pairing) {

        try {
            return pairings.insert(org, pairing);
        } catch (DuplicateKeyException e) {
            throw PairingGuards.alreadyPaired();
        }
    }

    private Instant now() {

        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ConflictException closed() {

        return new ConflictException("MENTORING_PROPOSAL_CLOSED", "This proposal is no longer open");
    }
}
