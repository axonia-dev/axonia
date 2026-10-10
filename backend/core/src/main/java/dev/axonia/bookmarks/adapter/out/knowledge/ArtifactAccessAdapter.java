package dev.axonia.bookmarks.adapter.out.knowledge;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.ArtifactAccess;
import dev.axonia.knowledge.api.Artifact;
import dev.axonia.knowledge.api.Artifacts;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ArtifactAccessAdapter implements ArtifactAccess {

    private final Artifacts artifactService;

    @Override
    public Artifact get(long artifactId) {
        return artifactService.get(artifactId);
    }
}
