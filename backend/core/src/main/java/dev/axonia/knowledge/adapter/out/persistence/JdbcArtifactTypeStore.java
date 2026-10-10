package dev.axonia.knowledge.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.domain.StoredArtifactType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcArtifactTypeStore implements ArtifactTypeStore {

    private final ArtifactTypeRepository artifactTypeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StoredArtifactType> listByLabel(String organizationId) {

        return artifactTypeRepository.findByOrganizationIdOrderByLabelAsc(organizationId).stream()
                .map(JdbcArtifactTypeStore::toType).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredArtifactType> find(String organizationId, Long id) {

        return artifactTypeRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcArtifactTypeStore::toType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredArtifactType> findAll(String organizationId, Collection<Long> ids) {

        return artifactTypeRepository.findByOrganizationIdAndIdIn(organizationId, ids).stream()
                .map(JdbcArtifactTypeStore::toType).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean labelExists(String organizationId, String label) {

        return artifactTypeRepository.existsByOrganizationIdAndLabel(organizationId, label);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> artifactCountsByType(String organizationId) {

        return artifactTypeRepository.countArtifactsByType(organizationId).stream()
                .collect(Collectors.toMap(ArtifactTypeRepository.TypeArtifactCount::typeId,
                        ArtifactTypeRepository.TypeArtifactCount::artifactCount));
    }

    @Override
    @Transactional(readOnly = true)
    public long artifactCount(Long typeId) {

        return artifactTypeRepository.countArtifactRefs(typeId);
    }

    @Override
    public void reassignArtifacts(Long source, Long target) {

        artifactTypeRepository.reassignArtifactRefs(source, target);
    }

    @Override
    @Transactional
    public StoredArtifactType save(StoredArtifactType type) {

        return toType(artifactTypeRepository.save(toEntity(type)));
    }

    @Override
    public void delete(StoredArtifactType type) {

        artifactTypeRepository.delete(toEntity(type));
    }

    static StoredArtifactType toType(ArtifactTypeEntity entity) {

        return StoredArtifactType.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .label(entity.getLabel()).icon(entity.getIcon()).accentColor(entity.getAccentColor())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static ArtifactTypeEntity toEntity(StoredArtifactType type) {

        return ArtifactTypeEntity.builder().id(type.id()).organizationId(type.organizationId()).label(type.label())
                .icon(type.icon()).accentColor(type.accentColor()).createdAt(type.createdAt())
                .updatedAt(type.updatedAt()).version(type.version()).build();
    }
}
