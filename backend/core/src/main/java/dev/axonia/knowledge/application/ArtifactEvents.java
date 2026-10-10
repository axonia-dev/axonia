package dev.axonia.knowledge.application;

import dev.axonia.knowledge.api.ArtifactContributed;
import dev.axonia.knowledge.domain.StoredArtifact;

final class ArtifactEvents {

    private ArtifactEvents() {
    }

    static ArtifactContributed contributed(StoredArtifact artifact) {

        return new ArtifactContributed(artifact.id(), artifact.communityId(), artifact.contributorUserId(),
                artifact.tags().stream().toList());
    }
}
