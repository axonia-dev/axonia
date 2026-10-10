package dev.axonia.events.application;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a session's facilitator reference against a community's members - the same free-text resolution
 * {@code PresenterResolver} and {@code TopicMemberResolver} already run, never trusted as a free string carried
 * straight onto the row. {@code AwaitingCloseProducer} addresses the {@code SESSION_AWAITING_CLOSE} nudge to whoever
 * this column names, so an unresolved reference would become a message recipient nobody vetted.
 *
 * <p>
 * An unchanged value is left alone rather than re-resolved: {@code UpdateSessionRequest} is a full replace, so a client
 * editing an unrelated field - the room, the duration - echoes back whatever facilitator the row already carries.
 * Re-validating that echo on every save would lock a session out of being edited at all once its facilitator has left
 * the community, or once an erasure surrogate has replaced the id, until someone thinks to clear the field first purely
 * to unblock a save that had nothing to do with it.
 */
@Component
@RequiredArgsConstructor
public class FacilitatorResolver {

    private final CommunityAccess communityAccess;

    /**
     * {@code query} is the request's own {@code facilitatorUserId}; {@code storedFacilitatorUserId} is what the row
     * already carries. Null or blank clears the field, which is an ordinary state: a session with nobody named still
     * closes, it just has no reminder recipient. An unresolvable or ambiguous reference otherwise refuses the write,
     * distinctly - a caller told only "not a member" has nothing to act on if the real problem is that two members
     * share the name they typed.
     */
    public String resolve(Long communityId, String storedFacilitatorUserId, String query) {

        if (query == null || query.isBlank()) {
            return null;
        }
        if (query.equals(storedFacilitatorUserId)) {
            return storedFacilitatorUserId;
        }
        return switch (communityAccess.resolveMember(communityId, query)) {
            case MemberMatch.Matched matched -> matched.userId();
            case MemberMatch.Ambiguous ambiguous -> throw new ValidationException("FACILITATOR_AMBIGUOUS",
                    ambiguous.candidates() + " members of this community match: " + query);
            case MemberMatch.NotFound ignored -> throw new ValidationException("FACILITATOR_NOT_A_MEMBER",
                    "The facilitator must be a member of this community");
        };
    }
}
