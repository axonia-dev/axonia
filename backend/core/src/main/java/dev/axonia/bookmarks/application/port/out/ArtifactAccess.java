package dev.axonia.bookmarks.application.port.out;

import dev.axonia.knowledge.api.Artifact;

public interface ArtifactAccess {

    Artifact get(long artifactId);
}
