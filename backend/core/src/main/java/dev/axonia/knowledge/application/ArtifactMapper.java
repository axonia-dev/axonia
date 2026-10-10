package dev.axonia.knowledge.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.Artifact;
import dev.axonia.knowledge.api.ArtifactStatus;
import dev.axonia.knowledge.api.Provenance;
import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.StoredArtifactType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ArtifactMapper {

    private final ArtifactTypeStore artifactTypeStore;

    /**
     * Annotated even though {@link #toArtifacts} already is: this is a self-invocation, which Spring's proxy cannot
     * intercept, so {@code toArtifacts}'s own annotation never fires on this path.
     */
    @Transactional(readOnly = true)
    public Artifact toArtifact(StoredArtifact e) {

        return toArtifacts(List.of(e)).getFirst();
    }

    /**
     * The type label of every artifact in the batch, resolved once rather than once per row - see
     * {@code SessionBatchLookups.sectionTypesOf} for the same shape on the events side. {@code IN} does not need to
     * preserve any order here: the result is a lookup keyed by type id, replayed onto each artifact by
     * {@link #toArtifact(StoredArtifact, Map)} below.
     */
    @Transactional(readOnly = true)
    public List<Artifact> toArtifacts(List<StoredArtifact> entities) {

        var typeLabelsById = typeLabelsById(entities);
        return entities.stream().map(e -> toArtifact(e, typeLabelsById)).toList();
    }

    private Artifact toArtifact(StoredArtifact e, Map<Long, String> typeLabelsById) {

        var tags = Objects.requireNonNullElse(e.tags(), Set.<String>of()).stream().sorted().toList();
        return Artifact.builder().id(e.id()).communityId(e.communityId()).typeId(e.typeId())
                .type(typeLabelsById.get(e.typeId())).title(e.title()).description(e.description()).url(e.url())
                .tags(tags).contributorUserId(e.contributorUserId()).provenance(provenance(e))
                .status(ArtifactStatus.valueOf(e.status())).loveCount(Objects.requireNonNullElse(e.loveCount(), 0))
                .createdAt(e.createdAt()).updatedAt(e.updatedAt()).build();
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
    private Map<Long, String> typeLabelsById(List<StoredArtifact> entities) {

        var typeIds = entities.stream().map(StoredArtifact::typeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        var organizationId = entities.getFirst().organizationId();
        return artifactTypeStore.findAll(organizationId, typeIds).stream()
                .collect(Collectors.toMap(StoredArtifactType::id, StoredArtifactType::label));
    }

    private Provenance provenance(StoredArtifact e) {

        if (e.provenanceSessionId() == null) {
            return null;
        }
        return Provenance.builder().sessionId(e.provenanceSessionId()).segmentId(e.provenanceSegmentId())
                .label(e.provenanceLabel()).build();
    }
}
