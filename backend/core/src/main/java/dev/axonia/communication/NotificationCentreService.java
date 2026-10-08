package dev.axonia.communication;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * The panel's application service: it supplies the signed-in member and the clock, and owns nothing else. The reads and
 * the marks both live in {@code internal}, and the member's identity is never a parameter a caller could get wrong.
 *
 * <p>
 * {@code page}'s clamp overlaps deliberately with the one {@link NotificationCentreReads#page} already applies to a
 * non-positive size: this one is the API's own contract about what a caller may ask for, turning a hostile
 * {@code size=100000} into a bounded read at the boundary rather than relying on the repository to save it.
 */
@Service
@RequiredArgsConstructor
public class NotificationCentreService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationCentreReads reads;
    private final CurrentUser currentUser;
    private final Clock clock;

    public NotificationPage page(Instant before, Long beforeId, int size) {

        return reads.page(currentUser.userId(), before, beforeId, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    public int unreadCount() {

        return reads.unreadCount(currentUser.userId());
    }

    @Transactional
    public void markRead(long id) {

        reads.markRead(currentUser.userId(), id, clock.instant());
    }

    @Transactional
    public void markAllRead() {

        reads.markAllRead(currentUser.userId(), clock.instant());
    }
}
