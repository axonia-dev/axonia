package dev.axonia.bookmarks.adapter.out.events;

import java.util.Optional;

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
     * Mirrors the frontend's {@code sessionTitle}: the first titled slot across sections, then the session's own title,
     * then a generic label, so a saved row names a session as its page does.
     */
    private static String displayTitle(Session s) {
        return s.sections().stream().flatMap(sec -> sec.slots().stream()).map(Slot::title)
                .filter(SessionAccessAdapter::isTitled).findFirst()
                .or(() -> Optional.ofNullable(s.title()).filter(SessionAccessAdapter::isTitled)).orElse("Session");
    }

    private static boolean isTitled(String title) {
        return title != null && !title.isBlank();
    }

    private static int slotCount(Session s) {
        return s.sections().stream().mapToInt(sec -> sec.slots().size()).sum();
    }
}
