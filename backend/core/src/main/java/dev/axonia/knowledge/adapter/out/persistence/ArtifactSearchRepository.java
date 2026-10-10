package dev.axonia.knowledge.adapter.out.persistence;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;

interface ArtifactSearchRepository {

    Page<ArtifactEntity> search(String organizationId, Long communityId, ArtifactFilter filter, Pageable pageable);

    List<ArtifactSession> sessionsWithArtifacts(String organizationId, Long communityId);

    LibraryStats stats(String organizationId, Long communityId);
}
