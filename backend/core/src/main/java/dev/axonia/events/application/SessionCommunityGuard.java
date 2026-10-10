package dev.axonia.events.application;

import java.time.Clock;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * The guard shared by every write that stores a session id arriving in a request body: org scoping is not enough,
 * because a session id is visible across the whole Organization on purpose and is sequential, so an id from a request
 * proves nothing about which community it belongs to on its own. A {@code null} id is a no-op - many callers make no
 * claim about a session at all.
 *
 * <p>
 * Named {@code requireSessionInCommunity} deliberately: {@code CrossCommunityReferenceGuardTest} matches a guard call
 * by that name (or {@code requireOriginSessionInCommunity}, or {@link #requireUpcomingSessionInCommunity}) appearing in
 * the same member as the write, or one call away from it - so every call site here must stay within that same member as
 * the id it guards.
 */
@Component
@RequiredArgsConstructor
public class SessionCommunityGuard {

    /** A session in one of these can never answer anything, so nothing may be pinned to it. */
    private static final Set<String> NEVER_UPCOMING = Set.of("CANCELLED", "SKIPPED", "HELD");

    private final SessionStore sessionStore;
    private final Clock clock;

    public void requireSessionInCommunity(String organizationId, Long sessionId, long communityId) {

        if (sessionId != null) {
            inCommunityOrThrow(organizationId, sessionId, communityId);
        }
    }

    /**
     * The checkpoint's own guard: a checkpoint is where an item will be answered, so the session has to be one that can
     * still answer it. Pinning to a cancelled, skipped or already-held session strands the item for good - it is
     * pinned, so {@code pinLateBound} skips it; {@code clearPinsFor} never fires for that session again; and no
     * follow-up pass lists it. Origin keeps the looser guard on purpose: an origin session is history, and a held or
     * even cancelled session may legitimately have raised something.
     */
    public void requireUpcomingSessionInCommunity(String organizationId, Long sessionId, long communityId) {

        if (sessionId == null) {
            return;
        }
        var session = inCommunityOrThrow(organizationId, sessionId, communityId);
        if (NEVER_UPCOMING.contains(session.getStatus()) || !session.getScheduledAt().isAfter(clock.instant())) {
            throw new ConflictException("CHECKPOINT_NOT_UPCOMING",
                    "An action item can only be answered at a session that is still upcoming");
        }
    }

    private SessionRecord inCommunityOrThrow(String organizationId, Long sessionId, long communityId) {

        return sessionStore.findByOrganizationIdAndId(organizationId, sessionId)
                .filter(session -> session.getCommunityId().equals(communityId))
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }
}
