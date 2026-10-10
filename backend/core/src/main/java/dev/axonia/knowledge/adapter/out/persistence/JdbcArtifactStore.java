package dev.axonia.knowledge.adapter.out.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.LibraryStats;
import dev.axonia.knowledge.domain.StoredArtifact;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcArtifactStore implements ArtifactStore {

    private final ArtifactRepository artifactRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredArtifact> find(String organizationId, Long id) {

        return artifactRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcArtifactStore::toArtifact);
    }

    @Override
    @Transactional
    public StoredArtifact save(StoredArtifact artifact) {

        return toArtifact(artifactRepository.save(toEntity(artifact)));
    }

    @Override
    public void delete(StoredArtifact artifact) {

        artifactRepository.delete(toEntity(artifact));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StoredArtifact> search(String organizationId, Long communityId, ArtifactFilter filter,
            Pageable pageable) {

        return artifactRepository.search(organizationId, communityId, filter, pageable)
                .map(JdbcArtifactStore::toArtifact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactSession> sessionsWithArtifacts(String organizationId, Long communityId) {

        return artifactRepository.sessionsWithArtifacts(organizationId, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryStats stats(String organizationId, Long communityId) {

        return artifactRepository.stats(organizationId, communityId);
    }

    static StoredArtifact toArtifact(ArtifactEntity entity) {

        return StoredArtifact.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).typeId(entity.getTypeId()).title(entity.getTitle())
                .description(entity.getDescription()).url(entity.getUrl())
                .contributorUserId(entity.getContributorUserId()).provenanceSessionId(entity.getProvenanceSessionId())
                .provenanceSegmentId(entity.getProvenanceSegmentId()).provenanceLabel(entity.getProvenanceLabel())
                .status(entity.getStatus()).searchDocument(entity.getSearchDocument()).loveCount(entity.getLoveCount())
                .tags(tagsOf(entity.getTags())).createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static ArtifactEntity toEntity(StoredArtifact artifact) {

        return ArtifactEntity.builder().id(artifact.id()).organizationId(artifact.organizationId())
                .communityId(artifact.communityId()).typeId(artifact.typeId()).title(artifact.title())
                .description(artifact.description()).url(artifact.url()).contributorUserId(artifact.contributorUserId())
                .provenanceSessionId(artifact.provenanceSessionId()).provenanceSegmentId(artifact.provenanceSegmentId())
                .provenanceLabel(artifact.provenanceLabel()).status(artifact.status())
                .searchDocument(artifact.searchDocument()).loveCount(artifact.loveCount()).tags(refsOf(artifact.tags()))
                .createdAt(artifact.createdAt()).updatedAt(artifact.updatedAt()).version(artifact.version()).build();
    }

    private static Set<String> tagsOf(Set<ArtifactTagRef> refs) {

        if (refs == null) {
            return null;
        }
        return refs.stream().map(ArtifactTagRef::getTag).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<ArtifactTagRef> refsOf(Set<String> tags) {

        if (tags == null) {
            return null;
        }
        return tags.stream().map(t -> ArtifactTagRef.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
