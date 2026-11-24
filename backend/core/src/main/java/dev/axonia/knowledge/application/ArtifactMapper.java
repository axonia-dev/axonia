package dev.axonia.knowledge.application;

import java.time.LocalDate;
import java.util.Comparator;
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
import dev.axonia.knowledge.application.port.out.SessionLabels;
import dev.axonia.knowledge.domain.ArtifactSession;
import dev.axonia.knowledge.domain.SessionLabel;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.StoredArtifactType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ArtifactMapper {

    private final ArtifactTypeStore artifactTypeStore;
    private final SessionLabels sessionLabels;

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
        var sessionLabelsById = sessionLabels.of(entities.stream().map(StoredArtifact::provenanceSessionId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return entities.stream().map(e -> toArtifact(e, typeLabelsById, sessionLabelsById)).toList();
    }

    /**
     * Every session the library can filter by, labelled in one lookup and newest first; a session that no longer
     * resolves keeps its count, goes unnamed and sinks to the end.
     */
    @Transactional(readOnly = true)
    public List<ArtifactSession> toSessions(List<ArtifactSession> sessions) {

        var labelsById = sessionLabels.of(sessions.stream().map(ArtifactSession::sessionId).toList());
        return sessions.stream().map(session -> labelled(session, labelsById.get(session.sessionId())))
                .sorted(NEWEST_HELD_FIRST).toList();
    }

    private static final Comparator<ArtifactSession> NEWEST_HELD_FIRST = Comparator
            .comparing(ArtifactSession::heldOn, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(ArtifactSession::sessionId).reversed();

    private static ArtifactSession labelled(ArtifactSession session, SessionLabel label) {

        if (label == null) {
            return session;
        }
        return session.toBuilder().name(label.name()).heldOn(label.heldOn()).build();
    }

    private Artifact toArtifact(StoredArtifact e, Map<Long, String> typeLabelsById,
            Map<Long, SessionLabel> sessionLabelsById) {

        var tags = Objects.requireNonNullElse(e.tags(), Set.<String>of()).stream().sorted().toList();
        return Artifact.builder().id(e.id()).communityId(e.communityId()).typeId(e.typeId())
                .type(typeLabelsById.get(e.typeId())).title(e.title()).description(e.description()).url(e.url())
                .tags(tags).contributorUserId(e.contributorUserId()).provenance(provenance(e, sessionLabelsById))
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

    private static Provenance provenance(StoredArtifact e, Map<Long, SessionLabel> sessionLabelsById) {

        if (e.provenanceSessionId() == null) {
            return null;
        }
        var provenance = Provenance.builder().sessionId(e.provenanceSessionId()).segmentId(e.provenanceSegmentId())
                .label(e.provenanceLabel());
        var session = sessionLabelsById.get(e.provenanceSessionId());
        if (session != null) {
            provenance.seriesTitle(session.seriesTitle()).heldOn(session.heldOn());
        }
        return provenance.build();
    }
}
