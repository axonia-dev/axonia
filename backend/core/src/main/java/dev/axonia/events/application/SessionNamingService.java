package dev.axonia.events.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionNaming;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SessionName;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Renaming touches nothing but the title, so it is open on every status: a cancelled or skipped session still sits on
 * the calendar and in the history, and a name is how an organizer says what it was. Nothing is sent, and nothing about
 * when or whether the session happens changes.
 */
@Service
@RequiredArgsConstructor
public class SessionNamingService implements SessionNaming {

    private final SessionStore sessionStore;
    private final SessionMapper sessionMapper;
    private final SessionAuthorizer sessionAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional
    public Session rename(Long sessionId, String title) {

        var session = sessionStore.findByOrganizationIdAndId(organizationContext.organizationId(), sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        sessionAuthorizer.requireScheduleManager(session.getCommunityId(), currentUser.userId());
        var saved = sessionStore.save(session.toBuilder().title(SessionName.ownTitle(title)).build());
        return sessionMapper.toSessionDetail(saved);
    }
}
