package dev.axonia.knowledge;

import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactTagRef;

final class ArtifactEvents {

    private ArtifactEvents() {
    }

    static ArtifactContributed contributed(ArtifactEntity artifact) {

        return new ArtifactContributed(artifact.getId(), artifact.getCommunityId(), artifact.getContributorUserId(),
                artifact.getTags().stream().map(ArtifactTagRef::getTag).toList());
    }
}
