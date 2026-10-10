package dev.axonia.knowledge.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.axonia.knowledge.domain.StoredArtifactType;

public interface ArtifactTypeStore {

    List<StoredArtifactType> listByLabel(String organizationId);

    Optional<StoredArtifactType> find(String organizationId, Long id);

    List<StoredArtifactType> findAll(String organizationId, Collection<Long> ids);

    boolean labelExists(String organizationId, String label);

    /** How many artifacts carry each type id, across the organization's every community. */
    Map<Long, Long> artifactCountsByType(String organizationId);

    long artifactCount(Long typeId);

    /** Moves every artifact of type {@code source} to type {@code target}. */
    void reassignArtifacts(Long source, Long target);

    StoredArtifactType save(StoredArtifactType type);

    void delete(StoredArtifactType type);
}
