package dev.axonia.access;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.access.internal.ApplicablePermissions;
import dev.axonia.access.internal.RoleBindingEntity;
import dev.axonia.access.internal.RoleBindingRepository;
import dev.axonia.access.internal.RoleEntity;
import dev.axonia.access.internal.RolePermissionEntity;
import dev.axonia.access.internal.RoleRepository;
import lombok.RequiredArgsConstructor;

/**
 * System-initiated access seeding for newly founded Organizations and their joiners. Unlike {@link RoleBindingService},
 * methods here are NOT gated by {@link AuthorizationGate}: a freshly provisioned organization has no bindings yet, and
 * the caller (the module founding it) has already decided the outcome. Every method here must execute inside a
 * transaction whose RLS scope is {@code organizationId} (see {@code dev.axonia.OrganizationScope}); none of them read
 * the security context.
 */
@Service
@RequiredArgsConstructor
public class AccessSeeder {

    private static final String OWNER_ROLE_NAME = "Owner";
    private static final String MEMBER_ROLE_NAME = "Member";

    private final RoleRepository roleRepository;
    private final RoleBindingRepository bindingRepository;
    private final ApplicablePermissions applicablePermissions;

    /**
     * Seed a fresh enterprise Organization's role catalogue and crown its Owner. The Owner is bound to the native
     * bootstrap administrator account that provisioning creates, as a {@code USER} binding on that account's subject.
     * Binding the Owner to an IdP group instead is what produced the bootstrap deadlock: the customer's first admin
     * cannot authenticate through their own directory until that directory is federated, federating it requires
     * {@code sso:write}, and no token carries the owner group before the connection exists, so the Organization was
     * born with nobody able to administer it. A native account breaks that cycle because it can sign in immediately.
     *
     * <p>
     * The account is deliberately retained after federation as the break-glass administrator, the way back in when the
     * identity provider itself is unreachable or misconfigured. Ownership is expected to move to a {@code GROUP}
     * binding once SCIM is syncing the customer's directory; this binding is the starting point, not the end state. The
     * native account is never linked to a federated identity by email, so that move is an explicit act by the
     * administrator, not an automatic merge.
     *
     * <p>
     * The Self-hosted edition passes a group, because its identity provider is configured before the first start.
     */
    @Transactional
    public void seedEnterpriseAccess(String organizationId, OwnerBinding owner) {
        seedAccess(organizationId, FoundingRoles.ofAnOrganization(owner));
    }

    /**
     * Seed a fresh Organization's two system roles as {@code roles} describes them, and bind its Owner. The Owner role
     * carries every permission that applies in the Organization, so its registry row must already exist.
     */
    @Transactional
    public void seedAccess(String organizationId, FoundingRoles roles) {
        var ownerRole = roleRepository.save(RoleEntity.builder().organizationId(organizationId).name(OWNER_ROLE_NAME)
                .description(roles.ownerDescription()).system(true).slug("owner")
                .permissions(permissionsFor(organizationId)).build());
        roleRepository.save(RoleEntity.builder().organizationId(organizationId).name(MEMBER_ROLE_NAME)
                .description(roles.memberDescription()).system(true).slug("member").permissions(new HashSet<>())
                .build());
        var owner = roles.owner();
        bindingRepository
                .save(RoleBindingEntity.builder().organizationId(organizationId).subjectType(owner.subjectType())
                        .subjectId(owner.subjectId()).subjectLabel(owner.label()).roleId(ownerRole.getId()).build());
    }

    /**
     * Bind {@code group} to the Organization's Owner role unless it already is, leaving every other binding as it is.
     * The Self-hosted edition calls it at each start, so a newly configured owner group gains the role while the
     * previous one keeps it until an Owner removes it. Returns whether a binding was added.
     */
    @Transactional
    public boolean ensureGroupOwner(String organizationId, OwnerBinding.Group group) {
        var owner = roleRepository.findByOrganizationIdAndName(organizationId, OWNER_ROLE_NAME)
                .orElseThrow(() -> new NotFoundException("Owner role missing for organization: " + organizationId));
        if (bindingRepository.existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId(organizationId,
                group.subjectType(), group.subjectId(), owner.getId())) {
            return false;
        }
        bindingRepository
                .save(RoleBindingEntity.builder().organizationId(organizationId).subjectType(group.subjectType())
                        .subjectId(group.subjectId()).subjectLabel(group.label()).roleId(owner.getId()).build());
        return true;
    }

    /**
     * Bind a joiner to the hub's Member role. Returns {@code false} (no write) when the subject already holds any USER
     * binding in the org: membership is idempotent, a binding IS the membership.
     */
    @Transactional
    public boolean bindMemberIfAbsent(String organizationId, String subject, String subjectLabel) {
        var alreadyBound = bindingRepository.findByOrganizationId(organizationId).stream()
                .anyMatch(b -> "USER".equals(b.getSubjectType()) && subject.equals(b.getSubjectId()));
        if (alreadyBound) {
            return false;
        }
        var member = roleRepository.findByOrganizationIdAndName(organizationId, MEMBER_ROLE_NAME)
                .orElseThrow(() -> new NotFoundException("Member role missing for hub: " + organizationId));
        bindingRepository.save(RoleBindingEntity.builder().organizationId(organizationId).subjectType("USER")
                .subjectId(subject).subjectLabel(subjectLabel).roleId(member.getId()).build());
        return true;
    }

    /**
     * Remove every USER binding the subject holds in the org (the inverse of {@link #bindMemberIfAbsent}), for a hub
     * departure. All of the subject's roles go at once: leaving the hub is leaving, not demotion. Returns {@code false}
     * (no write) when the subject holds none: no binding IS no membership.
     */
    @Transactional
    public boolean unbindUser(String organizationId, String subject) {
        var bindings = bindingRepository.findByOrganizationId(organizationId).stream()
                .filter(b -> "USER".equals(b.getSubjectType()) && subject.equals(b.getSubjectId())).toList();
        bindings.forEach(bindingRepository::delete);
        return !bindings.isEmpty();
    }

    private Set<RolePermissionEntity> permissionsFor(String organizationId) {
        return applicablePermissions.in(organizationId).stream()
                .map(p -> RolePermissionEntity.builder().permission(p.code()).build())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
