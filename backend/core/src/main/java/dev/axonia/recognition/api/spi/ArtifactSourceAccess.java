package dev.axonia.recognition.api.spi;

import java.util.List;
import java.util.Optional;

import dev.axonia.recognition.api.ContributionSource;

/** Resolves an artifact's authoritative attribution facts, org-scoped; empty when it does not exist. */
public interface ArtifactSourceAccess {

    Optional<ContributionSource> artifactSource(String organizationId, Long artifactId);

    /** Ids of artifacts the user contributed, org-scoped: the read-time source of capture credit. */
    List<Long> artifactsCapturedBy(String organizationId, String userId);
}
