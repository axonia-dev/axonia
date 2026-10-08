package dev.axonia.bookmarks.internal;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.ArtifactAccess;
import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ArtifactAccessAdapter implements ArtifactAccess {

    private final ArtifactService artifactService;

    @Override
    public Artifact get(long artifactId) {
        return artifactService.get(artifactId);
    }
}
