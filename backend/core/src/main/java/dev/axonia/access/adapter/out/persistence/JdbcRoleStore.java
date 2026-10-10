package dev.axonia.access.adapter.out.persistence;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcRoleStore implements RoleStore {

    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleDefinition> find(Long id) {

        return roleRepository.findById(id).map(JdbcRoleStore::toRole);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDefinition> findAll(Collection<Long> ids) {

        return StreamSupport.stream(roleRepository.findAllById(ids).spliterator(), false).map(JdbcRoleStore::toRole)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDefinition> listIn(String organizationId) {

        return roleRepository.findByOrganizationId(organizationId).stream().map(JdbcRoleStore::toRole).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleDefinition> findByName(String organizationId, String name) {

        return roleRepository.findByOrganizationIdAndName(organizationId, name).map(JdbcRoleStore::toRole);
    }

    @Override
    @Transactional
    public RoleDefinition save(RoleDefinition role) {

        return toRole(roleRepository.save(toEntity(role)));
    }

    @Override
    public void deleteById(Long id) {

        roleRepository.deleteById(id);
    }

    static RoleDefinition toRole(RoleEntity entity) {

        return RoleDefinition.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .name(entity.getName()).description(entity.getDescription()).system(entity.isSystem())
                .slug(entity.getSlug())
                .permissions(entity.getPermissions().stream().map(RolePermissionEntity::getPermission).toList())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static RoleEntity toEntity(RoleDefinition role) {

        return RoleEntity.builder().id(role.id()).organizationId(role.organizationId()).name(role.name())
                .description(role.description()).system(role.system()).slug(role.slug())
                .permissions(
                        role.permissions().stream().map(code -> RolePermissionEntity.builder().permission(code).build())
                                .collect(Collectors.toCollection(HashSet::new)))
                .createdAt(role.createdAt()).updatedAt(role.updatedAt()).version(role.version()).build();
    }
}
