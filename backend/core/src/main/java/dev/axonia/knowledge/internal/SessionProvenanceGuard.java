package dev.axonia.knowledge.internal;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.knowledge.SessionAccess;
import dev.axonia.knowledge.SessionRef;
import lombok.RequiredArgsConstructor;

/**
 * The two questions any write carrying a session reference has to ask: is that session this community's, and is that
 * segment that session's.
 *
 * <p>
 * Row-Level Security answers neither. Both communities live in one Organization, and ids are visible across it
 * deliberately, so an id from a request body is org-visible, sequential, and means nothing about who may write to it.
 * Its own component rather than a private method on either caller because there were two callers and only one of them
 * asked: {@code ResourceService.attach} was fixed in July and {@code ArtifactService.capture}, which writes the same
 * two columns and lands on the same page, was not.
 */
@Component
@RequiredArgsConstructor
public class SessionProvenanceGuard {

    private final SessionAccess sessionAccess;

    /**
     * Refuses a session that is not this community's, as a 404: the caller has no business knowing it exists. Returns
     * the {@link SessionRef} it fetched, so a caller that needs more than the provenance check - {@link #isHeld} - does
     * not pay for a second read of the same row. {@code null} when {@code sessionId} is itself {@code null}: that is a
     * legal input here, not a session to look up.
     */
    public SessionRef requireSessionInCommunity(Long sessionId, Long communityId) {

        if (sessionId == null) {
            return null;
        }
        var session = sessionAccess.session(sessionId);
        if (!Objects.equals(session.communityId(), communityId)) {
            throw new NotFoundException("Session not found: " + sessionId);
        }
        return session;
    }

    /** Refuses a segment that belongs to a different session, as a 400: the session itself was legitimate. */
    public void requireSegmentInSession(Long segmentId, Long sessionId) {

        if (segmentId == null) {
            return;
        }
        if (sessionAccess.sessionIdOfSlot(segmentId).filter(id -> id.equals(sessionId)).isEmpty()) {
            throw new ValidationException("SEGMENT_NOT_IN_SESSION", "The segment does not belong to this session");
        }
    }

    /**
     * Whether the session has already been held - open to any member once true, schedule-management-gated before. Takes
     * the {@link SessionRef} {@link #requireSessionInCommunity} already fetched, rather than a raw id, so this cannot
     * NPE on an unboxed {@code null}; a {@code null} reference fails closed to {@code false} (not held), which routes
     * the caller into the stricter schedule-management check rather than past it.
     */
    public boolean isHeld(SessionRef session) {

        return session != null && session.held();
    }
}
