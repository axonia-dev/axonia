package dev.axonia.bookmarks.adapter.out.events;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.SessionAccess;
import dev.axonia.bookmarks.domain.SavedSession;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.Slot;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class SessionAccessAdapter implements SessionAccess {

    private final Sessions sessionService;

    @Override
    public SavedSession get(long sessionId) {
        Session s = sessionService.get(sessionId);
        return SavedSession.builder().id(s.id()).communityId(s.communityId()).title(displayTitle(s)).status(s.status())
                .scheduledAt(s.scheduledAt()).occurrenceDate(s.occurrenceDate())
                .facilitatorUserId(s.facilitatorUserId()).slotCount(slotCount(s)).recordingUrl(s.recordingUrl())
                .build();
    }

    /**
     * Mirrors the frontend's {@code sessionTitle}: a session has no title of its own — its content lives in its
     * sections' slots. Surface the first titled slot across sections, and fall back to a generic label for a
     * not-yet-shaped session. The {@code Session.title} field itself is not consulted, to match the frontend rule
     * exactly.
     */
    private static String displayTitle(Session s) {
        return s.sections().stream().flatMap(sec -> sec.slots().stream()).map(Slot::title)
                .filter(t -> t != null && !t.isBlank()).findFirst().orElse("Session");
    }

    private static int slotCount(Session s) {
        return s.sections().stream().mapToInt(sec -> sec.slots().size()).sum();
    }
}
