package dev.axonia.knowledge.api;

import java.util.Collection;
import java.util.Map;

/** Artifact titles by id, within the current organization. An id that no longer resolves is absent. */
public interface ArtifactTitles {

    Map<Long, String> titlesByIds(Collection<Long> ids);

    /** The same lookup, with the community whose library holds each artifact. */
    Map<Long, TitledArtifact> artifactsByIds(Collection<Long> ids);
}
