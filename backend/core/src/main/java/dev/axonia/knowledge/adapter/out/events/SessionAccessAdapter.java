package dev.axonia.knowledge.adapter.out.events;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.SlotSessions;
import dev.axonia.knowledge.application.port.out.SessionAccess;
import dev.axonia.knowledge.domain.SessionRef;
import lombok.RequiredArgsConstructor;

@Component("knowledgeSessionAccessAdapter")
@RequiredArgsConstructor
class SessionAccessAdapter implements SessionAccess {

    private final Sessions sessionService;
    private final SlotSessions slotSessionReader;

    @Override
    public SessionRef session(long sessionId) {

        var session = sessionService.get(sessionId);
        return new SessionRef(session.id(), session.communityId(), session.status() == SessionStatus.HELD);
    }

    @Override
    public Optional<Long> sessionIdOfSlot(long slotId) {

        return slotSessionReader.sessionIdOf(slotId);
    }
}
