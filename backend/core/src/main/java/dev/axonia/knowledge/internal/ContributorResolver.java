package dev.axonia.knowledge.internal;

import org.springframework.stereotype.Component;

import dev.axonia.ActionableForbiddenException;
import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.community.MemberMatch;
import dev.axonia.knowledge.CommunityAccess;
import lombok.RequiredArgsConstructor;

/**
 * Decides whose contribution an artifact is: the caller's own, or another member's that the caller is crediting.
 *
 * <p>
 * An absent or blank reference means the caller, which is the live path, and so does the caller's own user id. A caller
 * the community does not let credit contributions may otherwise only name themselves exactly, by display name or email,
 * and every other reference gets one identical refusal before the roster is asked: a match, no match or an ambiguity
 * would each tell a plain member whether some other member's name or email contains a fragment. Only a caller with the
 * Credit contributions right has the reference resolved against the roster, where a reference matching nobody or
 * several members refuses the write. There is nothing else honest to do: {@code contributor_user_id} is NOT NULL, so
 * there is no "nobody" to fall back to, and falling back to the caller is precisely the failure this type exists to
 * prevent - an organizer backfilling a library credits themselves with the whole guild's work, one expertise signal per
 * tag, and it looks exactly like success.
 *
 * <p>
 * The resolution itself belongs to {@code community}, which owns the roster, and is reached through this module's
 * anti-corruption port.
 */
@Component
@RequiredArgsConstructor
public class ContributorResolver {

    private static final String REFUSED = "Only an organizer, or a member the community lets credit contributions, "
            + "may credit a contribution to another member.";

    private final CommunityAccess communityAccess;

    public String resolve(long communityId, String callerId, String reference) {

        // Mirrors dev.axonia.events.internal.TopicMemberResolver#resolveProposer (blank -> caller; NotFound/Ambiguous
        // -> refuse). CPD misses the duplication because the differing error-code literals break the token run below
        // the 100-token floor.
        if (reference == null || reference.isBlank()) {
            return callerId;
        }
        var query = reference.strip();
        if (query.equals(callerId)) {
            return callerId;
        }
        if (communityAccess.canCreditContributions(communityId, callerId)) {
            return member(communityId, query);
        }
        if (communityAccess.identifiesExactly(callerId, query)) {
            return callerId;
        }
        throw new ActionableForbiddenException(REFUSED + " Omit contributor to record this contribution as your own.");
    }

    /**
     * Who an existing artifact should credit instead. Unlike {@link #resolve} there is no caller to fall back to: a
     * correction always names somebody, and naming nobody placeable refuses it the same way capture does.
     */
    public String resolveCorrection(long communityId, String callerId, String reference) {

        if (!communityAccess.canCreditContributions(communityId, callerId)) {
            throw new ForbiddenException(REFUSED);
        }
        return member(communityId, reference);
    }

    private String member(long communityId, String reference) {

        var query = reference.strip();
        return switch (communityAccess.resolveMember(communityId, query)) {
            case MemberMatch.Matched matched -> matched.userId();
            // The count the directory already computed is reported: told only "ambiguous", a caller cannot tell two
            // members who genuinely share a name from a reference that was simply too vague.
            case MemberMatch.Ambiguous ambiguous -> throw new ConflictException("CONTRIBUTOR_AMBIGUOUS",
                    ambiguous.candidates() + " members of this community match: " + query);
            case MemberMatch.NotFound ignored ->
                throw new ConflictException("CONTRIBUTOR_NOT_FOUND", "No member of this community matches: " + query);
        };
    }
}
