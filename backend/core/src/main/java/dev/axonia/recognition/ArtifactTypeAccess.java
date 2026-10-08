package dev.axonia.recognition;

import java.util.Collection;
import java.util.Map;

/**
 * Resolves the type badge of captured artifacts, owned by the {@code knowledge} module. Artifact ids that no longer
 * resolve are omitted from the result, so callers must tolerate missing keys.
 */
public interface ArtifactTypeAccess {

    Map<Long, ArtifactBadge> artifactTypes(Collection<Long> artifactIds);
}
