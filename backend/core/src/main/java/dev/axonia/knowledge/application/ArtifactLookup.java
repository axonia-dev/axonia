package dev.axonia.knowledge.application;

import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.shared.api.error.NotFoundException;

final class ArtifactLookup {

    private ArtifactLookup() {
    }

    static StoredArtifact findOrThrow(ArtifactStore artifactStore, String organizationId, Long id) {

        return artifactStore.find(organizationId, id)
                .orElseThrow(() -> new NotFoundException("Artifact not found: " + id));
    }
}
