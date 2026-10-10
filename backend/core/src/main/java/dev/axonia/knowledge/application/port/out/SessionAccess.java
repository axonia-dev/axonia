package dev.axonia.knowledge.application.port.out;

import java.util.Optional;

import dev.axonia.knowledge.domain.SessionRef;

/** Knowledge's window into the events context, for validating attachment references. */
public interface SessionAccess {

    /** The session, org-scoped; throws NotFoundException when it is not visible in the caller's organization. */
    SessionRef session(long sessionId);

    /** The session a slot (segment) belongs to; empty when the slot does not exist. */
    Optional<Long> sessionIdOfSlot(long slotId);
}
