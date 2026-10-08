package dev.axonia.bookmarks;

import dev.axonia.knowledge.Artifact;

public interface ArtifactAccess {

    Artifact get(long artifactId);
}
