package dev.axonia.events.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.SessionDeletion;
import dev.axonia.events.api.SlotCleared;
import dev.axonia.events.api.spi.SessionDeletionParticipant;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionDeletionService implements SessionDeletion {

    private final SessionStore sessionStore;
    private final AttendanceStore attendanceStore;
    private final ActionItemStore actionItemStore;
    private final TopicLifecycle topicLifecycle;
    private final SessionAuthorizer sessionAuthorizer;
    private final List<SessionDeletionParticipant> participants;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Each slot leaves the way a removed slot does - its topic back to the backlog, its expertise credit withdrawn -
     * and every row pointing at the session goes before the session itself.
     */
    @Override
    @Transactional
    public void delete(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var session = sessionStore.findByOrganizationIdAndId(organizationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        sessionAuthorizer.requireOrganizer(session, currentUser.userId());
        requireDeletable(session);

        var communityId = session.getCommunityId();
        // Cancelling already sent the topics back to the backlog, from where one may since have been rescheduled.
        var releasesTopics = !"CANCELLED".equals(session.getStatus());
        slotsOf(session).forEach(slot -> {
            if (releasesTopics) {
                topicLifecycle.release(organizationId, slot.getTopicId());
            }
            eventPublisher.publishEvent(new SlotCleared(slot.getId(), communityId));
        });
        participants.forEach(participant -> participant.sessionDeleted(organizationId, communityId, sessionId));
        attendanceStore.deleteAllForSession(organizationId, sessionId);
        actionItemStore.clearPinsFor(organizationId, communityId, sessionId, clock.instant());
        sessionStore.delete(session);
    }

    private static void requireDeletable(SessionRecord session) {

        if ("HELD".equals(session.getStatus())) {
            throw new ConflictException("SESSION_ALREADY_HELD",
                    "A session that took place is part of the community's history and cannot be deleted");
        }
        if (session.getSeriesId() != null) {
            throw new ConflictException("SESSION_IN_SERIES",
                    "A session of a series cannot be deleted: skip or cancel it instead");
        }
    }

    private static List<SlotRecord> slotsOf(SessionRecord session) {

        return Objects.requireNonNullElse(session.getSections(), List.<SectionRecord>of()).stream()
                .flatMap(section -> Objects.requireNonNullElse(section.getSlots(), List.<SlotRecord>of()).stream())
                .toList();
    }
}
