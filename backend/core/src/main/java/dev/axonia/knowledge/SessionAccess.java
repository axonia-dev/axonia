package dev.axonia.knowledge;

import java.util.Optional;

/** Knowledge's window into the events context, for validating attachment references. */
public interface SessionAccess {

    /** The session, org-scoped; throws NotFoundException when it is not visible in the caller's organization. */
    SessionRef session(long sessionId);

    /** The session a slot (segment) belongs to; empty when the slot does not exist. */
    Optional<Long> sessionIdOfSlot(long slotId);
}
