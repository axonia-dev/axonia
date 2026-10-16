package dev.axonia.events.api.spi;

import java.util.Collection;
import java.util.Map;

/** What each session left in the library: implemented by knowledge, which already depends on events. */
public interface SessionTraceAccess {

    Map<Long, ArtifactTrace> artifactTraces(String organizationId, long communityId, Collection<Long> sessionIds);
}
