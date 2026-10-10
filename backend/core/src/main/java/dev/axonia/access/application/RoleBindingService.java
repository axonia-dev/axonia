package dev.axonia.access.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.OrganizationOwners;
import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.DirectoryGroupsPolicy;
import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroupConfirmation;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleBindingService implements OrganizationOwners {

    private static final String GROUP = "GROUP";

    private final RoleBindingStore bindingStore;
    private final RoleStore roleStore;
    private final AuthorizationGate gate;
    private final OrganizationContext organization;
    private final DirectoryGroupConfirmation directoryGroupConfirmation;
    private final RoleCeiling ceiling;
    private final OrganizationOwnership ownership;
    private final GroupBindingApplicability groupBindingApplicability;

    @Transactional(readOnly = true)
    public List<RoleBinding> list() {

        gate.requirePermission(Permission.ROLE_READ);
        return bindingStore.listIn(organization.organizationId()).stream().map(this::toModel).toList();
    }

    @Transactional
    public RoleBinding create(CreateRoleBindingRequest r) {

        gate.requirePermission(Permission.ROLE_ASSIGN);
        guardSubjectType(r.subjectType());
        guardGroupBindingApplies(r.subjectType());
        var role = requireOwnedRole(r.roleId());
        ceiling.guardAssignable(role);
        guardNotAlreadyBound(r, role);
        var saved = bindingStore.save(
                SubjectBinding.builder().organizationId(organization.organizationId()).subjectType(r.subjectType())
                        .subjectId(r.subjectId()).subjectLabel(r.subjectLabel()).roleId(r.roleId()).build());
        return toModel(saved);
    }

    private static final Set<String> SUBJECT_TYPES = Set.of("USER", GROUP);

    private void guardSubjectType(String subjectType) {

        if (!SUBJECT_TYPES.contains(subjectType)) {
            throw new ValidationException("invalid_subject_type", "Subject type must be USER or GROUP: " + subjectType);
        }
    }

    /**
     * In an Organization with no directory (see {@link DirectoryGroupsPolicy}), no token reaching it can ever carry a
     * {@code groups} claim for {@code DefaultAuthorizationGate} to match. A {@code GROUP} binding written there is
     * unreachable by construction - it would grant nothing, forever, while reading on the page as a live grant. Refused
     * at the subject rather than at the role, because what does not apply is the kind of subject, not which role it
     * names.
     */
    private void guardGroupBindingApplies(String subjectType) {

        if (GROUP.equals(subjectType) && !groupBindingApplicability.applyIn(organization.organizationId())) {
            throw new ValidationException("group_binding_not_applicable",
                    "A community has no directory to read a group from, so a group cannot be bound to a role here.");
        }
    }

    /**
     * A binding IS the grant, so a second identical one grants nothing: it only splits one authority across two rows
     * that both have to be revoked before the role is really gone. Refused rather than silently absorbed, the same way
     * {@link RoleService} refuses a duplicate role name - the caller asked for something that is already true, and
     * saying so is more honest than a no-op that looks like a fresh grant. The unique constraint on
     * {@code role_binding} enforces the same rule one layer down, for writers that never come through here.
     */
    private void guardNotAlreadyBound(CreateRoleBindingRequest r, RoleDefinition role) {

        if (bindingStore.exists(organization.organizationId(), r.subjectType(), r.subjectId(), r.roleId())) {
            throw new ConflictException("role_already_bound", "Already holds the " + role.name() + " role.");
        }
    }

    private RoleDefinition requireOwnedRole(Long roleId) {

        return roleStore.find(roleId).filter(role -> role.organizationId().equals(organization.organizationId()))
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
    }

    /**
     * Releases the directory group this binding names, so that the roles bound to its key start reaching its members.
     *
     * <p>
     * SCIM provisioning holds a group whose reconciliation key a binding was already waiting on, because the SCIM
     * client chooses that key itself and would otherwise be able to walk into any pre-bound role. This is the only way
     * out of that state, and it is gated on {@code role:assign} for exactly that reason: {@code scim:write} is
     * precisely the permission whose holder must not be able to release their own push.
     *
     * <p>
     * The binding named by {@code id} only addresses the group. What is released is the <em>key</em>, so the ceiling is
     * checked over every binding on it - see {@link #guardEveryRoleOnTheKeyIsAssignable}.
     */
    @Transactional
    public void confirmGroup(Long id) {

        gate.requirePermission(Permission.ROLE_ASSIGN);
        var binding = requireOwnedGroupBinding(id);
        guardEveryRoleOnTheKeyIsAssignable(binding.subjectId());
        if (!directoryGroupConfirmation.confirm(organization.organizationId(), binding.subjectId())) {
            throw new NotFoundException("No directory group has been provisioned yet for: " + binding.subjectId());
        }
    }

    /**
     * The caller's ceiling, applied to every role bound to {@code subjectId} rather than only to the one whose binding
     * id was passed.
     *
     * <p>
     * Confirmation is stamped on the directory group, so it takes effect per key: the moment {@code confirmed_at} is
     * set, <em>every</em> {@code GROUP} binding naming that key starts conferring. Several are ordinary - a binding is
     * unique on subject plus role, so a second role on the same group is a normal write - which is what makes a
     * per-binding check unsound. A delegated administrator could otherwise bind some trivial role of their own to a key
     * an Owner binding is already waiting on, confirm through that trivial binding, and release the Owner binding with
     * it. Even with nobody attacking, a group carrying both an Owner and a Member binding must not be releasable by a
     * member-level administrator.
     */
    private void guardEveryRoleOnTheKeyIsAssignable(String subjectId) {

        bindingStore.findBySubject(organization.organizationId(), GROUP, subjectId).stream().map(SubjectBinding::roleId)
                .distinct().map(this::requireOwnedRole).forEach(ceiling::guardAssignable);
    }

    private SubjectBinding requireOwnedGroupBinding(Long id) {

        return bindingStore.find(id).filter(b -> organization.organizationId().equals(b.organizationId()))
                .filter(b -> GROUP.equals(b.subjectType()))
                .orElseThrow(() -> new NotFoundException("Group role binding not found: " + id));
    }

    @Transactional
    public void delete(Long id) {

        gate.requirePermission(Permission.ROLE_ASSIGN);
        var binding = bindingStore.find(id).orElse(null);
        if (binding == null) {
            return;
        }
        var role = roleStore.find(binding.roleId()).orElse(null);
        if (role != null) {
            ceiling.guardRevocable(role);
        }
        guardLastOwnerRetained(binding);
        guardLastManagementBindingRetained(binding);
        bindingStore.deleteById(id);
    }

    /**
     * An Organization with no direct USER Owner cannot be repaired from inside the product: ownership transfer,
     * organization deletion and SCIM all sit behind the Owner role. Only relevant when the binding being removed is
     * itself a binding to the Owner role - revoking some unrelated role from the Organization's sole Owner is ordinary
     * role administration, not the last-Owner case, even though {@link #soleUserOwner} would also answer true for that
     * subject. Deliberately not {@link #isSoleOwner}: that count is over every binding to the Owner role regardless of
     * subject type, so a GROUP Owner binding sitting alongside the last USER Owner binding would silently mask this
     * guard. {@link OrganizationOwnership#isSoleUserOwner} counts direct USER bindings only, matching that class's own
     * notion of who actually holds Owner.
     */
    private void guardLastOwnerRetained(SubjectBinding binding) {

        if (!"USER".equals(binding.subjectType()) || !ownership.isOwnerRoleBinding(binding)) {
            return;
        }
        if (ownership.isSoleUserOwner(binding.organizationId(), binding.subjectId())) {
            throw new ConflictException("last_owner",
                    "At least one person must keep the Owner role. Grant it to somebody else first.");
        }
    }

    /**
     * The {@link RoleService} guard keeps role <em>definitions</em> from losing the last grant of a management
     * permission; this is its mirror for bindings. Unbinding the only subject still assigned to a role that grants
     * {@code role:write} or {@code role:assign} would leave the organization unable to author or assign roles - a
     * self-inflicted lock-out - so it is refused. Removing a binding is allowed as long as some other binding keeps
     * each management permission live, which lets administration be re-homed to another group before the old binding is
     * dropped.
     */
    private void guardLastManagementBindingRetained(SubjectBinding target) {

        var rolesById = roleStore.listIn(target.organizationId()).stream()
                .collect(Collectors.toMap(RoleDefinition::id, r -> r));
        var targetRole = rolesById.get(target.roleId());
        if (targetRole == null) {
            return;
        }
        var otherBindings = bindingStore.listIn(target.organizationId()).stream()
                .filter(a -> !a.id().equals(target.id())).toList();
        for (var guard : MANAGEMENT_GUARDS) {
            if (!grants(targetRole, guard.perm())) {
                continue;
            }
            var keptByAnotherBinding = otherBindings.stream().map(a -> rolesById.get(a.roleId()))
                    .filter(Objects::nonNull).anyMatch(r -> grants(r, guard.perm()));
            if (!keptByAnotherBinding) {
                throw new ConflictException(guard.lastCode(),
                        "At least one group must keep " + guard.perm().code() + " " + guard.lastReason());
            }
        }
    }

    private boolean grants(RoleDefinition role, Permission perm) {

        return role.permissions().stream().anyMatch(p -> perm.code().equals(p));
    }

    /**
     * A management permission whose last live binding may not be removed, with the conflict code/reason raised if it
     * is.
     */
    private record ManagementGuard(Permission perm, String lastCode, String lastReason) {
    }

    private static final List<ManagementGuard> MANAGEMENT_GUARDS = List.of(
            new ManagementGuard(Permission.ROLE_WRITE, "role_write_last_binding", "so roles stay manageable."),
            new ManagementGuard(Permission.ROLE_ASSIGN, "role_assign_last_binding", "so roles stay assignable."));

    /**
     * Whether {@code subject} is the sole Owner of this Organization. For the account-erasure orchestrator's guard
     * against leaving a hub ownerless - unlike {@link #list()}/{@link #assigneesOf}, this is not gated behind
     * {@link AuthorizationGate}/{@link OrganizationContext}: the caller acts across an arbitrary Organization with no
     * HTTP request and no permission of its own, the same reason {@code AccessSeeder} bypasses the gate for
     * system-initiated seeding. The rule itself lives in {@link OrganizationOwnership}.
     */
    @Override
    public boolean isSoleOwner(String organizationId, String subject) {

        return ownership.isSoleOwner(organizationId, subject);
    }

    /**
     * Whether the caller holds this Organization's seeded Owner role through any binding the gate matches: their own,
     * or one of their groups'. {@code MembershipService.changeRole} lets an Owner grant {@code ORGANIZER} in a
     * community left with none; an Organization whose Owners hold the role through a directory group (every Self-hosted
     * one, and an enterprise whose ownership moved to its directory) would otherwise have nobody who can.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean callerHoldsOwnerRole(String organizationId) {

        return ownership.ownerRoleId(organizationId).filter(ownerRoleId -> gate.currentRoleIds().contains(ownerRoleId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<RoleBinding> assigneesOf(Long roleId) {

        gate.requirePermission(Permission.ROLE_READ);
        return bindingStore.boundTo(roleId).stream().map(this::toModel).toList();
    }

    private RoleBinding toModel(SubjectBinding e) {

        return RoleBinding.builder().id(e.id()).subjectType(e.subjectType()).subjectId(e.subjectId())
                .subjectLabel(e.subjectLabel()).roleId(e.roleId()).build();
    }
}
