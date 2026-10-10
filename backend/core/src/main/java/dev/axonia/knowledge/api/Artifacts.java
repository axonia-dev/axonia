package dev.axonia.knowledge.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** The artifact library as other modules reach it. */
public interface Artifacts {

    Artifact capture(String slug, CreateArtifactRequest request);

    Artifact get(Long id);

    Page<Artifact> search(String slug, ArtifactFilter filter, Pageable pageable);
}
