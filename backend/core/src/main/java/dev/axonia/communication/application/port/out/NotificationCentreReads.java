package dev.axonia.communication.application.port.out;

import java.time.Instant;

import dev.axonia.communication.domain.NotificationPage;
import dev.axonia.communication.domain.PanelViewer;

/**
 * A member's own panel: what is in it, how much of it is unread, and the two marks that change that. Every method takes
 * the viewer explicitly rather than reading the security context itself, so the one place a caller could name the wrong
 * person is whichever application service sits above this port - never this port itself, and never this
 * {@code JdbcNotificationCentre} behind it. A type the viewer hides is out of every one of them: not listed, not
 * counted, never marked.
 */
public interface NotificationCentreReads {

    NotificationPage page(PanelViewer viewer, Instant before, Long beforeId, int size);

    int unreadCount(PanelViewer viewer);

    /** Returns rows affected: zero for an entry that is already read, and zero for one belonging to somebody else. */
    int markRead(PanelViewer viewer, long id, Instant now);

    int markAllRead(PanelViewer viewer, Instant now);
}
