package dev.axonia.events.internal;

import org.springframework.stereotype.Component;

import dev.axonia.ActionableForbiddenException;
import dev.axonia.ConflictException;
import dev.axonia.community.MemberMatch;
import dev.axonia.events.CommunityAccess;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a community-member reference on a topic - the proposer it is credited to, and the assignee the work is
 * dispatched to. Split out of TopicService so the same "a name that matches nobody or several refuses the write" rule
 * lives in one place for both.
 */
@Component
@RequiredArgsConstructor
public class TopicMemberResolver {

    private final CommunityAccess communityAccess;

    /**
     * Resolves whose proposal this is. An absent or blank reference means the caller's own, which is the live path. A
     * supplied reference is an organizer's act of crediting someone else, and one that does not resolve to exactly one
     * member of the community refuses the write: {@code proposer_user_id} is NOT NULL, so there is no "nobody" to fall
     * back to, and falling back to the caller would turn every backfilled OFFER into expertise the organizer never had,
     * while looking exactly like success.
     */
    public String resolveProposer(long communityId, String callerId, String reference) {

        // Mirrors dev.axonia.knowledge.internal.ContributorResolver#resolve (blank -> caller; NotFound/Ambiguous ->
        // refuse), but any supplied proposer is an organizer's act, where a caller without the contributor axis may
        // still name themselves exactly. CPD misses the duplication because the differing error-code literals break
        // the token run below the 100-token floor.
        if (reference == null || reference.isBlank()) {
            return callerId;
        }
        if (!communityAccess.isOrganizer(communityId, callerId)) {
            // Actionable, deliberately: the message names the field to drop. A caller that supplied its own name is
            // one keystroke from success, and a refusal that only said "not permitted" would send it looking for a
            // permission it does not need.
            throw new ActionableForbiddenException("Only an organizer may credit a proposal to another member. "
                    + "Omit proposer to record this proposal as your own.");
        }
        var query = reference.strip();
        return switch (communityAccess.resolveMember(communityId, query)) {
            case MemberMatch.Matched matched -> matched.userId();
            // The count the directory already computed is reported: told only "ambiguous", a caller cannot tell two
            // members who genuinely share a name from a reference that was simply too vague.
            case MemberMatch.Ambiguous ambiguous -> throw new ConflictException("PROPOSER_AMBIGUOUS",
                    ambiguous.candidates() + " members of this community match: " + query);
            case MemberMatch.NotFound ignored ->
                throw new ConflictException("PROPOSER_NOT_FOUND", "No member of this community matches: " + query);
        };
    }

    /**
     * Resolves whom the work is dispatched to. Blank -> unassigned (null); present -> must resolve to exactly one
     * member, else the write is refused. Crediting the wrong owner is worse than leaving it unassigned, so a reference
     * that matches nobody or several is never settled by guessing.
     */
    public String resolveAssignee(long communityId, String reference) {

        if (reference == null || reference.isBlank()) {
            return null;
        }
        var query = reference.strip();
        return switch (communityAccess.resolveMember(communityId, query)) {
            case MemberMatch.Matched matched -> matched.userId();
            case MemberMatch.Ambiguous ambiguous -> throw new ConflictException("ASSIGNEE_AMBIGUOUS",
                    ambiguous.candidates() + " members of this community match: " + query);
            case MemberMatch.NotFound ignored ->
                throw new ConflictException("ASSIGNEE_NOT_FOUND", "No member of this community matches: " + query);
        };
    }
}
