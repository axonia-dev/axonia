package dev.axonia.knowledge.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.knowledge.api.ArtifactType;
import dev.axonia.knowledge.api.ArtifactTypes;
import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.StoredArtifactType;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtifactTypeService implements ArtifactTypes {

    private final ArtifactTypeStore artifactTypeStore;
    private final OrganizationContext organizationContext;
    private final AuthorizationGate gate;

    @Override
    @Transactional(readOnly = true)
    public List<ArtifactType> list() {

        var organizationId = organizationContext.organizationId();
        var counts = artifactTypeStore.artifactCountsByType(organizationId);
        return artifactTypeStore.listByLabel(organizationId).stream()
                .map(e -> toType(e, counts.getOrDefault(e.id(), 0L))).toList();
    }

    @Transactional
    public ArtifactType create(CreateArtifactTypeRequest request) {

        gate.requirePermission(Permission.LIBRARY_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        if (artifactTypeStore.labelExists(organizationId, request.label())) {
            throw new ConflictException("ARTIFACT_TYPE_EXISTS", "Artifact type already exists: " + request.label());
        }
        var saved = artifactTypeStore.save(StoredArtifactType.builder().organizationId(organizationId)
                .label(request.label()).icon(request.icon()).accentColor(request.accentColor()).build());
        return toType(saved, 0L);
    }

    @Transactional
    public ArtifactType update(Long typeId, UpdateArtifactTypeRequest request) {

        gate.requirePermission(Permission.LIBRARY_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var entity = artifactTypeStore.find(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Artifact type not found: " + typeId));
        if (!entity.label().equals(request.label()) && artifactTypeStore.labelExists(organizationId, request.label())) {
            throw new ConflictException("ARTIFACT_TYPE_EXISTS", "Artifact type already exists: " + request.label());
        }
        var saved = artifactTypeStore.save(entity.toBuilder().label(request.label()).icon(request.icon())
                .accentColor(request.accentColor()).build());
        return toType(saved, artifactTypeStore.artifactCount(typeId));
    }

    @Transactional
    public void delete(Long typeId, Long reassignToId) {

        gate.requirePermission(Permission.LIBRARY_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var type = artifactTypeStore.find(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Artifact type not found: " + typeId));

        if (artifactTypeStore.artifactCount(typeId) > 0) {
            if (reassignToId == null) {
                throw new ConflictException("ARTIFACT_TYPE_IN_USE",
                        "Artifact type is in use; a reassignment target is required to delete it");
            }
            if (reassignToId.equals(typeId)) {
                throw new ConflictException("INVALID_REASSIGNMENT", "Cannot reassign to the deleted type");
            }
            artifactTypeStore.find(organizationId, reassignToId)
                    .orElseThrow(() -> new NotFoundException("Artifact type not found: " + reassignToId));
            artifactTypeStore.reassignArtifacts(typeId, reassignToId);
        }

        artifactTypeStore.delete(type);
    }

    private ArtifactType toType(StoredArtifactType e, long artifactCount) {

        return ArtifactType.builder().id(e.id()).label(e.label()).icon(e.icon()).accentColor(e.accentColor())
                .artifactCount(artifactCount).build();
    }
}
