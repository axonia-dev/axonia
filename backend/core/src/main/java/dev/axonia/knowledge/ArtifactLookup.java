package dev.axonia.knowledge;

import dev.axonia.NotFoundException;
import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactRepository;

final class ArtifactLookup {

    private ArtifactLookup() {
    }

    static ArtifactEntity findOrThrow(ArtifactRepository artifactRepository, String organizationId, Long id) {

        return artifactRepository.findByOrganizationIdAndId(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Artifact not found: " + id));
    }
}
