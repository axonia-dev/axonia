package dev.axonia.access;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.access.internal.ApplicablePermissions;
import dev.axonia.access.internal.RoleBindingRepository;
import dev.axonia.access.internal.RoleEntity;
import dev.axonia.access.internal.RolePermissionEntity;
import dev.axonia.access.internal.RoleRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final AuthorizationGate gate;
    private final OrganizationContext organization;
    private final RoleBindingRepository bindingRepository;
    private final ApplicablePermissions applicablePermissions;
    private final KnownPermissions knownPermissions;

    @Transactional(readOnly = true)
    public List<Role> list() {

        gate.requirePermission(Permission.ROLE_READ);
        return roleRepository.findByOrganizationId(organization.organizationId()).stream().map(this::toModel).toList();
    }

    /**
     * The roles bound to the current caller, by id and name, sorted by name. Ungated: reading one's own roles is
     * baseline, as {@code /access/me} already serves the permissions those roles grant. {@link #list()} cannot stand in
     * for it, because it requires {@code role:read}, which a plain member does not hold.
     */
    @Transactional(readOnly = true)
    public List<MyRole> myRoles() {

        var roleIds = gate.currentRoleIds();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByOrganizationId(organization.organizationId()).stream()
                .filter(r -> roleIds.contains(r.getId())).map(r -> new MyRole(r.getId(), r.getName()))
                .sorted(Comparator.comparing(MyRole::name)).toList();
    }

    @Transactional
    public Role create(CreateRoleRequest r) {

        gate.requirePermission(Permission.ROLE_WRITE);
        var organizationId = organization.organizationId();
        roleRepository.findByOrganizationIdAndName(organizationId, r.name()).ifPresent(x -> {
            throw new ConflictException("role_name_taken", "A role named '" + r.name() + "' already exists.");
        });
        guardPermissionsApplicable(r.permissions());
        guardPermissionsWithinCallerCeiling(r.permissions(), List.of());
        var saved = roleRepository.save(RoleEntity.builder().organizationId(organizationId).name(r.name())
                .description(r.description()).permissions(toPermissions(r.permissions())).build());
        return toModel(saved);
    }

    @Transactional
    public Role update(Long id, UpdateRoleRequest r) {

        gate.requirePermission(Permission.ROLE_WRITE);
        var entity = requireOwnedRole(id);
        if (entity.isSystem()) {
            throw new ConflictException("role_system", "System roles can't be modified.");
        }
        guardManagementPermissionsRetained(entity, r.permissions());
        guardPermissionsApplicable(r.permissions());
        var current = currentPermissionCodes(entity);
        var added = r.permissions().stream().filter(c -> !current.contains(c)).toList();
        var removed = current.stream().filter(c -> !r.permissions().contains(c)).toList();
        guardPermissionsWithinCallerCeiling(added, removed);
        roleRepository.findByOrganizationIdAndName(entity.getOrganizationId(), r.name())
                .filter(o -> !o.getId().equals(id)).ifPresent(x -> {
                    throw new ConflictException("role_name_taken", "A role named '" + r.name() + "' already exists.");
                });
        var updated = entity.toBuilder().name(r.name()).description(r.description())
                .permissions(toPermissions(r.permissions())).build();
        return toModel(roleRepository.save(updated));
    }

    @Transactional
    public void delete(Long id) {

        gate.requirePermission(Permission.ROLE_WRITE);
        var entity = requireOwnedRole(id);
        if (entity.isSystem()) {
            throw new ConflictException("role_system", "System roles can't be modified.");
        }
        guardManagementPermissionsRetained(entity, List.of());
        guardPermissionsWithinCallerCeiling(List.of(), currentPermissionCodes(entity));
        if (!bindingRepository.findByRoleId(id).isEmpty()) {
            throw new ConflictException("role_in_use", "Reassign its assignees before deleting this role.");
        }
        roleRepository.deleteById(id);
    }

    /**
     * A management permission whose loss could lock a organization out, with the conflict codes/reason raised if it
     * would.
     */
    private record ManagementGuard(Permission perm, String selfCode, String lastCode, String lastReason) {
    }

    private static final List<ManagementGuard> MANAGEMENT_GUARDS = List.of(
            new ManagementGuard(Permission.ROLE_WRITE, "role_write_self", "role_write_last",
                    "so roles stay manageable."),
            new ManagementGuard(Permission.ROLE_ASSIGN, "role_assign_self", "role_assign_last",
                    "so roles stay assignable."));

    /**
     * Prevents an admin from locking everyone out of role management. For each management permission, an edit or delete
     * that would strip it from {@code target} is rejected if the caller currently depends on that role, or if it is the
     * last role in the organization that still grants it. {@code role:write} keeps the catalogue authorable;
     * {@code role:assign} keeps roles assignable.
     */
    private void guardManagementPermissionsRetained(RoleEntity target, List<String> retainedCodes) {

        for (var guard : MANAGEMENT_GUARDS) {
            guardPermissionRetained(target, guard, retainedCodes);
        }
    }

    private void guardPermissionRetained(RoleEntity target, ManagementGuard guard, List<String> retainedCodes) {

        var perm = guard.perm();
        if (retainedCodes.contains(perm.code()) || !grants(target, perm)) {
            return;
        }
        if (gate.currentRoleIds().contains(target.getId())) {
            throw new ConflictException(guard.selfCode(),
                    "You can't remove " + perm.code() + " from a role you currently rely on.");
        }
        var anotherRoleGrantsIt = roleRepository.findByOrganizationId(target.getOrganizationId()).stream()
                .filter(r -> !r.getId().equals(target.getId())).anyMatch(r -> grants(r, perm));
        if (!anotherRoleGrantsIt) {
            throw new ConflictException(guard.lastCode(),
                    "At least one role must keep " + perm.code() + " " + guard.lastReason());
        }
    }

    /**
     * Rejects any permission that does not apply in the acting organization (see {@link PermissionCatalogue}) - e.g. an
     * SSO/SCIM permission on a role inside a self-serve hub. Defense in depth: the UI never offers these, so this only
     * blocks crafted requests. Unknown codes are left to {@link #toPermissions} to reject as unknown_permission.
     */
    private void guardPermissionsApplicable(List<String> codes) {

        var applicable = applicablePermissions.in(organization.organizationId());
        codes.stream().flatMap(c -> knownPermissions.byCode(c).stream()).filter(p -> !applicable.contains(p))
                .findFirst().ifPresent(p -> {
                    throw new ValidationException("permission_not_applicable",
                            "Permission " + p.code() + " isn't available in this workspace.");
                });
    }

    /**
     * Privilege ceiling on both directions of a role edit. A role author may only put permissions into a role that they
     * themselves hold, and may only take out permissions they themselves hold. Without the second half a delegated
     * role:write grant is authority over every permission in the catalogue: PUT permissions: [] on a role the author
     * cannot recreate, and everybody bound to it loses what it granted.
     */
    private void guardPermissionsWithinCallerCeiling(Collection<String> added, Collection<String> removed) {

        var held = gate.currentPermissions();
        var missing = Stream.concat(added.stream(), removed.stream()).flatMap(c -> knownPermissions.byCode(c).stream())
                .filter(p -> !held.contains(p)).map(Permission::code).distinct().sorted().toList();
        if (!missing.isEmpty()) {
            throw new ForbiddenException(
                    "A role can only be edited if every added or removed permission is already held; missing: "
                            + String.join(", ", missing));
        }
    }

    private static List<String> currentPermissionCodes(RoleEntity entity) {

        return entity.getPermissions().stream().map(RolePermissionEntity::getPermission).toList();
    }

    private boolean grants(RoleEntity entity, Permission perm) {

        return entity.getPermissions().stream().anyMatch(p -> perm.code().equals(p.getPermission()));
    }

    private RoleEntity requireOwnedRole(Long id) {

        var entity = roleRepository.findById(id).orElseThrow(() -> new NotFoundException("Role not found: " + id));
        if (!entity.getOrganizationId().equals(organization.organizationId())) {
            throw new NotFoundException("Role not found: " + id);
        }
        return entity;
    }

    private Set<RolePermissionEntity> toPermissions(List<String> codes) {

        return codes.stream().map(code -> {
            if (knownPermissions.byCode(code).isEmpty()) {
                throw new ValidationException("unknown_permission", "Unknown permission: " + code);
            }
            return RolePermissionEntity.builder().permission(code).build();
        }).collect(Collectors.toSet());
    }

    private Role toModel(RoleEntity e) {

        var perms = e.getPermissions().stream().flatMap(rp -> knownPermissions.byCode(rp.getPermission()).stream())
                .collect(Collectors.toSet());
        return Role.builder().id(e.getId()).name(e.getName()).description(e.getDescription()).system(e.isSystem())
                .slug(e.getSlug()).permissions(perms).build();
    }
}
