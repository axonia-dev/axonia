package dev.axonia.knowledge;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.Permission;
import dev.axonia.knowledge.internal.ArtifactTypeEntity;
import dev.axonia.knowledge.internal.ArtifactTypeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtifactTypeService {

    private final ArtifactTypeRepository artifactTypeRepository;
    private final OrganizationContext organizationContext;
    private final AuthorizationGate gate;

    @Transactional(readOnly = true)
    public List<ArtifactType> list() {

        var organizationId = organizationContext.organizationId();
        var counts = artifactTypeRepository.countArtifactsByType(organizationId).stream()
                .collect(Collectors.toMap(ArtifactTypeRepository.TypeArtifactCount::typeId,
                        ArtifactTypeRepository.TypeArtifactCount::artifactCount));
        return artifactTypeRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream()
                .map(e -> toType(e, counts.getOrDefault(e.getId(), 0L))).toList();
    }

    @Transactional
    public ArtifactType create(CreateArtifactTypeRequest request) {

        gate.requirePermission(Permission.LIBRARY_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        if (artifactTypeRepository.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("ARTIFACT_TYPE_EXISTS", "Artifact type already exists: " + request.label());
        }
        var saved = artifactTypeRepository.save(ArtifactTypeEntity.builder().organizationId(organizationId)
                .label(request.label()).icon(request.icon()).accentColor(request.accentColor()).build());
        return toType(saved, 0L);
    }

    @Transactional
    public ArtifactType update(Long typeId, UpdateArtifactTypeRequest request) {

        gate.requirePermission(Permission.LIBRARY_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var entity = artifactTypeRepository.findByOrganizationIdAndId(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Artifact type not found: " + typeId));
        if (!entity.getLabel().equals(request.label())
                && artifactTypeRepository.existsByOrganizationIdAndLabel(organizationId, request.label())) {
            throw new ConflictException("ARTIFACT_TYPE_EXISTS", "Artifact type already exists: " + request.label());
        }
        var saved = artifactTypeRepository.save(entity.toBuilder().label(request.label()).icon(request.icon())
                .accentColor(request.accentColor()).build());
        return toType(saved, artifactTypeRepository.countArtifactRefs(typeId));
    }

    @Transactional
    public void delete(Long typeId, Long reassignToId) {

        gate.requirePermission(Permission.LIBRARY_TYPE_WRITE);
        var organizationId = organizationContext.organizationId();
        var type = artifactTypeRepository.findByOrganizationIdAndId(organizationId, typeId)
                .orElseThrow(() -> new NotFoundException("Artifact type not found: " + typeId));

        if (artifactTypeRepository.countArtifactRefs(typeId) > 0) {
            if (reassignToId == null) {
                throw new ConflictException("ARTIFACT_TYPE_IN_USE",
                        "Artifact type is in use; a reassignment target is required to delete it");
            }
            if (reassignToId.equals(typeId)) {
                throw new ConflictException("INVALID_REASSIGNMENT", "Cannot reassign to the deleted type");
            }
            artifactTypeRepository.findByOrganizationIdAndId(organizationId, reassignToId)
                    .orElseThrow(() -> new NotFoundException("Artifact type not found: " + reassignToId));
            artifactTypeRepository.reassignArtifactRefs(typeId, reassignToId);
        }

        artifactTypeRepository.delete(type);
    }

    private ArtifactType toType(ArtifactTypeEntity e, long artifactCount) {

        return ArtifactType.builder().id(e.getId()).label(e.getLabel()).icon(e.getIcon())
                .accentColor(e.getAccentColor()).artifactCount(artifactCount).build();
    }
}
