package dev.axonia.bookmarks.adapter.out.events;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.SessionAccess;
import dev.axonia.bookmarks.domain.SavedSession;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Sessions;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class SessionAccessAdapter implements SessionAccess {

    private final Sessions sessionService;

    @Override
    public SavedSession get(long sessionId) {
        Session s = sessionService.get(sessionId);
        return SavedSession.builder().id(s.id()).communityId(s.communityId()).title(s.displayTitle()).status(s.status())
                .scheduledAt(s.scheduledAt()).occurrenceDate(s.occurrenceDate())
                .facilitatorUserId(s.facilitatorUserId()).slotCount(slotCount(s)).recordingUrl(s.recordingUrl())
                .build();
    }

    private static int slotCount(Session s) {
        return s.sections().stream().mapToInt(sec -> sec.slots().size()).sum();
    }
}
