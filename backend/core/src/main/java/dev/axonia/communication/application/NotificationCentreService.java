package dev.axonia.communication.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.application.port.out.CurrentUser;
import dev.axonia.communication.application.port.out.NotificationCentreReads;
import dev.axonia.communication.domain.NotificationPage;
import dev.axonia.communication.domain.PanelViewer;
import lombok.RequiredArgsConstructor;

/**
 * The panel's application service: it supplies the signed-in member and the clock, and owns nothing else. The reads and
 * the marks both live behind {@link NotificationCentreReads}, and the member's identity is never a parameter a caller
 * could get wrong. A session acting for the member reads the panel without the types private to the member, which
 * {@link NotificationEntryBuilders#privateToMember()} names.
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
    private final NotificationEntryBuilders builders;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationPage page(Instant before, Long beforeId, int size) {

        return reads.page(viewer(), before, beforeId, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public int unreadCount() {

        return reads.unreadCount(viewer());
    }

    @Transactional
    public void markRead(long id) {

        reads.markRead(viewer(), id, clock.instant());
    }

    @Transactional
    public void markAllRead() {

        reads.markAllRead(viewer(), clock.instant());
    }

    private PanelViewer viewer() {

        var hidden = currentUser.delegated() ? builders.privateToMember() : Set.<String>of();
        return new PanelViewer(currentUser.userId(), hidden);
    }
}
