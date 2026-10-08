package dev.axonia.knowledge.internal;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.events.SessionService;
import dev.axonia.events.SessionStatus;
import dev.axonia.events.SlotSessionReader;
import dev.axonia.knowledge.SessionAccess;
import dev.axonia.knowledge.SessionRef;
import lombok.RequiredArgsConstructor;

@Component("knowledgeSessionAccessAdapter")
@RequiredArgsConstructor
class SessionAccessAdapter implements SessionAccess {

    private final SessionService sessionService;
    private final SlotSessionReader slotSessionReader;

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
