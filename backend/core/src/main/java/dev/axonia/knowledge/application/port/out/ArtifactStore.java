package dev.axonia.knowledge.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.TagCount;

public interface ArtifactStore {

    Optional<StoredArtifact> find(String organizationId, Long id);

    StoredArtifact save(StoredArtifact artifact);

    void delete(StoredArtifact artifact);

    /** One community's active artifacts matching {@code filter}, in the order the filter asks for. */
    Page<StoredArtifact> search(String organizationId, Long communityId, ArtifactFilter filter, Pageable pageable);

    List<ArtifactSession> sessionsWithArtifacts(String organizationId, Long communityId);

    LibraryStats stats(String organizationId, Long communityId);

    /** The {@code limit} tags most carried by active artifacts captured at or after {@code since}, most first. */
    List<TagCount> trendingTags(String organizationId, Long communityId, Instant since, int limit);
}
