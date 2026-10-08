package dev.axonia.communication;

import java.time.Instant;

/**
 * A member's own panel: what is in it, how much of it is unread, and the two marks that change that. Every method takes
 * the member explicitly rather than reading the security context itself, so the one place a caller could name the wrong
 * person is whichever application service sits above this port - never this port itself, and never this
 * {@code internal} implementation behind it.
 */
public interface NotificationCentreReads {

    NotificationPage page(String userId, Instant before, Long beforeId, int size);

    int unreadCount(String userId);

    /** Returns rows affected: zero for an entry that is already read, and zero for one belonging to somebody else. */
    int markRead(String userId, long id, Instant now);

    int markAllRead(String userId, Instant now);
}
