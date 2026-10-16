package dev.axonia.knowledge.adapter.out.events;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.spi.ArtifactTrace;
import dev.axonia.events.api.spi.SessionTraceAccess;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class SessionTraceAccessAdapter implements SessionTraceAccess {

    private final ArtifactRepository artifactRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ArtifactTrace> artifactTraces(String organizationId, long communityId,
            Collection<Long> sessionIds) {

        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return artifactRepository.tallySessions(organizationId, communityId, sessionIds).stream()
                .collect(Collectors.toMap(ArtifactRepository.SessionArtifactTally::sessionId,
                        t -> new ArtifactTrace(t.artifactCount(), t.loveTotal())));
    }
}
