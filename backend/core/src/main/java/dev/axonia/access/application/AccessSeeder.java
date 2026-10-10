package dev.axonia.access.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AccessSeeding;
import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.FoundingRoles;
import dev.axonia.access.api.OwnerBinding;
import dev.axonia.access.api.Permission;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * System-initiated access seeding for newly founded Organizations and their joiners. Unlike {@link RoleBindingService},
 * methods here are NOT gated by {@link AuthorizationGate}: a freshly provisioned organization has no bindings yet, and
 * the caller (the module founding it) has already decided the outcome. Every method here must execute inside a
 * transaction whose RLS scope is {@code organizationId} (see {@code dev.axonia.shared.api.tenancy.OrganizationScope});
 * none of them read the security context.
 */
@Service
@RequiredArgsConstructor
public class AccessSeeder implements AccessSeeding {

    private static final String OWNER_ROLE_NAME = "Owner";
    private static final String MEMBER_ROLE_NAME = "Member";

    private final RoleStore roleStore;
    private final RoleBindingStore bindingStore;
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
    @Override
    @Transactional
    public void seedEnterpriseAccess(String organizationId, OwnerBinding owner) {
        seedAccess(organizationId, FoundingRoles.ofAnOrganization(owner));
    }

    /**
     * Seed a fresh Organization's two system roles as {@code roles} describes them, and bind its Owner. The Owner role
     * carries every permission that applies in the Organization, so its registry row must already exist.
     */
    @Override
    @Transactional
    public void seedAccess(String organizationId, FoundingRoles roles) {
        var ownerRole = roleStore.save(RoleDefinition.builder().organizationId(organizationId).name(OWNER_ROLE_NAME)
                .description(roles.ownerDescription()).system(true).slug("owner")
                .permissions(permissionsFor(organizationId)).build());
        roleStore.save(RoleDefinition.builder().organizationId(organizationId).name(MEMBER_ROLE_NAME)
                .description(roles.memberDescription()).system(true).slug("member").permissions(List.of()).build());
        var owner = roles.owner();
        bindingStore.save(SubjectBinding.builder().organizationId(organizationId).subjectType(owner.subjectType())
                .subjectId(owner.subjectId()).subjectLabel(owner.label()).roleId(ownerRole.id()).build());
    }

    /**
     * Bind {@code group} to the Organization's Owner role unless it already is, leaving every other binding as it is.
     * The Self-hosted edition calls it at each start, so a newly configured owner group gains the role while the
     * previous one keeps it until an Owner removes it. Returns whether a binding was added.
     */
    @Override
    @Transactional
    public boolean ensureGroupOwner(String organizationId, OwnerBinding.Group group) {
        var owner = roleStore.findByName(organizationId, OWNER_ROLE_NAME)
                .orElseThrow(() -> new NotFoundException("Owner role missing for organization: " + organizationId));
        if (bindingStore.exists(organizationId, group.subjectType(), group.subjectId(), owner.id())) {
            return false;
        }
        bindingStore.save(SubjectBinding.builder().organizationId(organizationId).subjectType(group.subjectType())
                .subjectId(group.subjectId()).subjectLabel(group.label()).roleId(owner.id()).build());
        return true;
    }

    /**
     * Bind a joiner to the hub's Member role. Returns {@code false} (no write) when the subject already holds any USER
     * binding in the org: membership is idempotent, a binding IS the membership.
     */
    @Override
    @Transactional
    public boolean bindMemberIfAbsent(String organizationId, String subject, String subjectLabel) {
        var alreadyBound = bindingStore.listIn(organizationId).stream()
                .anyMatch(b -> "USER".equals(b.subjectType()) && subject.equals(b.subjectId()));
        if (alreadyBound) {
            return false;
        }
        var member = roleStore.findByName(organizationId, MEMBER_ROLE_NAME)
                .orElseThrow(() -> new NotFoundException("Member role missing for hub: " + organizationId));
        bindingStore.save(SubjectBinding.builder().organizationId(organizationId).subjectType("USER").subjectId(subject)
                .subjectLabel(subjectLabel).roleId(member.id()).build());
        return true;
    }

    /**
     * Remove every USER binding the subject holds in the org (the inverse of {@link #bindMemberIfAbsent}), for a hub
     * departure. All of the subject's roles go at once: leaving the hub is leaving, not demotion. Returns {@code false}
     * (no write) when the subject holds none: no binding IS no membership.
     */
    @Override
    @Transactional
    public boolean unbindUser(String organizationId, String subject) {
        var bindings = bindingStore.listIn(organizationId).stream()
                .filter(b -> "USER".equals(b.subjectType()) && subject.equals(b.subjectId())).toList();
        bindings.forEach(bindingStore::delete);
        return !bindings.isEmpty();
    }

    private List<String> permissionsFor(String organizationId) {
        return applicablePermissions.in(organizationId).stream().map(Permission::code).toList();
    }
}
