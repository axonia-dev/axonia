package dev.axonia.knowledge.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactStatus;
import dev.axonia.knowledge.Provenance;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ArtifactMapper {

    private final ArtifactTypeRepository artifactTypeRepository;

    /**
     * Annotated even though {@link #toArtifacts} already is: this is a self-invocation, which Spring's proxy cannot
     * intercept, so {@code toArtifacts}'s own annotation never fires on this path.
     */
    @Transactional(readOnly = true)
    public Artifact toArtifact(ArtifactEntity e) {

        return toArtifacts(List.of(e)).getFirst();
    }

    /**
     * The type label of every artifact in the batch, resolved once rather than once per row - see
     * {@code SessionBatchLookups.sectionTypesOf} for the same shape on the events side. {@code IN} does not need to
     * preserve any order here: the result is a lookup keyed by type id, replayed onto each artifact by
     * {@link #toArtifact(ArtifactEntity, Map)} below.
     */
    @Transactional(readOnly = true)
    public List<Artifact> toArtifacts(List<ArtifactEntity> entities) {

        var typeLabelsById = typeLabelsById(entities);
        return entities.stream().map(e -> toArtifact(e, typeLabelsById)).toList();
    }

    private Artifact toArtifact(ArtifactEntity e, Map<Long, String> typeLabelsById) {

        var tags = Objects.requireNonNullElse(e.getTags(), Set.<ArtifactTagRef>of()).stream()
                .map(ArtifactTagRef::getTag).sorted().toList();
        return Artifact.builder().id(e.getId()).communityId(e.getCommunityId()).typeId(e.getTypeId())
                .type(typeLabelsById.get(e.getTypeId())).title(e.getTitle()).description(e.getDescription())
                .url(e.getUrl()).tags(tags).contributorUserId(e.getContributorUserId()).provenance(provenance(e))
                .status(ArtifactStatus.valueOf(e.getStatus()))
                .loveCount(Objects.requireNonNullElse(e.getLoveCount(), 0)).createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt()).build();
    }

    /**
     * A miss here - a null type id, or a type id no longer in the catalog - reads the same way a lookup miss did before
     * this was batched: {@code typeLabelsById.get(...)} returns {@code null}, and {@code Artifact.type} is
     * {@code null}. An empty {@code IN ()} is a syntax error in Postgres, and an artifact with no type at all is a real
     * case (the field is nullable), so an empty batch of type ids is answered without a query.
     *
     * <p>
     * Precondition: {@code entities} belongs to a single organization, read off the first entity. True at every call
     * site today ({@code ArtifactService.search}'s repository read is already scoped to one organization), but a future
     * caller passing a mixed-tenant batch would get silently wrong type labels for every entity outside the first's
     * organization.
     */
    private Map<Long, String> typeLabelsById(List<ArtifactEntity> entities) {

        var typeIds = entities.stream().map(ArtifactEntity::getTypeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        var organizationId = entities.getFirst().getOrganizationId();
        return artifactTypeRepository.findByOrganizationIdAndIdIn(organizationId, typeIds).stream()
                .collect(Collectors.toMap(ArtifactTypeEntity::getId, ArtifactTypeEntity::getLabel));
    }

    private Provenance provenance(ArtifactEntity e) {

        if (e.getProvenanceSessionId() == null) {
            return null;
        }
        return Provenance.builder().sessionId(e.getProvenanceSessionId()).segmentId(e.getProvenanceSegmentId())
                .label(e.getProvenanceLabel()).build();
    }
}
