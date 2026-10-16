package dev.axonia.events.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.axonia.events.api.MemoryTraces;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Slot;
import dev.axonia.events.api.spi.ArtifactTrace;
import dev.axonia.events.api.spi.SessionTraceAccess;
import dev.axonia.events.application.port.out.TalkLoveCounts;
import lombok.RequiredArgsConstructor;

/**
 * The traces of a whole page of past sessions, read with one call to each source rather than one per session: a
 * session's loves are its talks' loves plus its artifacts' loves.
 */
@Component
@RequiredArgsConstructor
class MemoryTraceAssembler {

    private static final ArtifactTrace NO_ARTIFACTS = new ArtifactTrace(0, 0);

    private final TalkLoveCounts talkLoveCounts;
    private final SessionTraceAccess sessionTraceAccess;

    Map<Long, MemoryTraces> tracesFor(String organizationId, long communityId, List<Session> sessions) {

        var lovesBySlot = talkLoveCounts.lovesBySlot(organizationId,
                sessions.stream().flatMap(MemoryTraceAssembler::slotIdsOf).toList());
        var artifacts = sessionTraceAccess.artifactTraces(organizationId, communityId,
                sessions.stream().map(Session::id).toList());
        return sessions.stream().collect(Collectors.toMap(Session::id, session -> {
            var talkLoves = slotIdsOf(session).mapToLong(id -> lovesBySlot.getOrDefault(id, 0L)).sum();
            var artifact = artifacts.getOrDefault(session.id(), NO_ARTIFACTS);
            return new MemoryTraces(talkLoves + artifact.loves(), isRecorded(session), artifact.count());
        }));
    }

    private static Stream<Long> slotIdsOf(Session session) {

        return session.sections().stream().flatMap(section -> section.slots().stream()).map(Slot::id);
    }

    private static boolean isRecorded(Session session) {

        return session.recordingUrl() != null && !session.recordingUrl().isBlank();
    }
}
