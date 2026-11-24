package dev.axonia.knowledge.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;
import dev.axonia.knowledge.domain.TagCount;

interface ArtifactSearchRepository {

    Page<ArtifactEntity> search(String organizationId, Long communityId, ArtifactFilter filter, Pageable pageable);

    List<ArtifactSession> sessionsWithArtifacts(String organizationId, Long communityId);

    LibraryStats stats(String organizationId, Long communityId);

    List<TagCount> trendingTags(String organizationId, Long communityId, Instant since, int limit);
}
