package dev.axonia.knowledge.internal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.knowledge.ArtifactFilter;
import dev.axonia.knowledge.ArtifactSession;
import dev.axonia.knowledge.LibraryStats;

interface ArtifactSearchRepository {

    Page<ArtifactEntity> search(String organizationId, Long communityId, ArtifactFilter filter, Pageable pageable);

    List<ArtifactSession> sessionsWithArtifacts(String organizationId, Long communityId);

    LibraryStats stats(String organizationId, Long communityId);
}
