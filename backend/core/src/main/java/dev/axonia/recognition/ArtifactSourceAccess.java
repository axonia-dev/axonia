package dev.axonia.recognition;

import java.util.List;
import java.util.Optional;

/** Resolves an artifact's authoritative attribution facts, org-scoped; empty when it does not exist. */
public interface ArtifactSourceAccess {

    Optional<ContributionSource> artifactSource(String organizationId, Long artifactId);

    /** Ids of artifacts the user contributed, org-scoped: the read-time source of capture credit. */
    List<Long> artifactsCapturedBy(String organizationId, String userId);
}
