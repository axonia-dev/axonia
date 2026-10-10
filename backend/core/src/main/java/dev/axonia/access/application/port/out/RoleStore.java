package dev.axonia.access.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.access.domain.RoleDefinition;

public interface RoleStore {

    Optional<RoleDefinition> find(Long id);

    List<RoleDefinition> findAll(Collection<Long> ids);

    List<RoleDefinition> listIn(String organizationId);

    Optional<RoleDefinition> findByName(String organizationId, String name);

    RoleDefinition save(RoleDefinition role);

    void deleteById(Long id);
}
