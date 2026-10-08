package dev.axonia.access.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface RoleRepository extends CrudRepository<RoleEntity, Long> {
    List<RoleEntity> findByOrganizationId(String organizationId);
    Optional<RoleEntity> findByOrganizationIdAndName(String organizationId, String name);
}
