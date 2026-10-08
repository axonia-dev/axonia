package dev.axonia.access;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.access.internal.GroupBindingApplicability;
import dev.axonia.access.internal.OrganizationOwnership;
import dev.axonia.access.internal.RoleBindingEntity;
import dev.axonia.access.internal.RoleBindingRepository;
import dev.axonia.access.internal.RoleCeiling;
import dev.axonia.access.internal.RoleEntity;
import dev.axonia.access.internal.RoleRepository;
import dev.axonia.identity.DirectoryGroupConfirmation;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleBindingService {

    private static final String GROUP = "GROUP";

    private final RoleBindingRepository repository;
    private final RoleRepository roleRepository;
    private final AuthorizationGate gate;
    private final OrganizationContext organization;
    private final DirectoryGroupConfirmation directoryGroupConfirmation;
    private final RoleCeiling ceiling;
    private final OrganizationOwnership ownership;
    private final GroupBindingApplicability groupBindingApplicability;

    @Transactional(readOnly = true)
    public List<RoleBinding> list() {

        gate.requirePermission(Permission.ROLE_READ);
        return repository.findByOrganizationId(organization.organizationId()).stream().map(this::toModel).toList();
    }

    @Transactional
    public RoleBinding create(CreateRoleBindingRequest r) {

        gate.requirePermission(Permission.ROLE_ASSIGN);
        guardSubjectType(r.subjectType());
        guardGroupBindingApplies(r.subjectType());
        var role = requireOwnedRole(r.roleId());
        ceiling.guardAssignable(role);
        guardNotAlreadyBound(r, role);
        var saved = repository.save(
                RoleBindingEntity.builder().organizationId(organization.organizationId()).subjectType(r.subjectType())
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
    private void guardNotAlreadyBound(CreateRoleBindingRequest r, RoleEntity role) {

        if (repository.existsByOrganizationIdAndSubjectTypeAndSubjectIdAndRoleId(organization.organizationId(),
                r.subjectType(), r.subjectId(), r.roleId())) {
            throw new ConflictException("role_already_bound", "Already holds the " + role.getName() + " role.");
        }
    }

    private RoleEntity requireOwnedRole(Long roleId) {

        return roleRepository.findById(roleId)
                .filter(role -> role.getOrganizationId().equals(organization.organizationId()))
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
        guardEveryRoleOnTheKeyIsAssignable(binding.getSubjectId());
        if (!directoryGroupConfirmation.confirm(organization.organizationId(), binding.getSubjectId())) {
            throw new NotFoundException("No directory group has been provisioned yet for: " + binding.getSubjectId());
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

        repository.findByOrganizationIdAndSubjectTypeAndSubjectId(organization.organizationId(), GROUP, subjectId)
                .stream().map(RoleBindingEntity::getRoleId).distinct().map(this::requireOwnedRole)
                .forEach(ceiling::guardAssignable);
    }

    private RoleBindingEntity requireOwnedGroupBinding(Long id) {

        return repository.findById(id).filter(b -> organization.organizationId().equals(b.getOrganizationId()))
                .filter(b -> GROUP.equals(b.getSubjectType()))
                .orElseThrow(() -> new NotFoundException("Group role binding not found: " + id));
    }

    @Transactional
    public void delete(Long id) {

        gate.requirePermission(Permission.ROLE_ASSIGN);
        var binding = repository.findById(id).orElse(null);
        if (binding == null) {
            return;
        }
        var role = roleRepository.findById(binding.getRoleId()).orElse(null);
        if (role != null) {
            ceiling.guardRevocable(role);
        }
        guardLastOwnerRetained(binding);
        guardLastManagementBindingRetained(binding);
        repository.deleteById(id);
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
    private void guardLastOwnerRetained(RoleBindingEntity binding) {

        if (!"USER".equals(binding.getSubjectType()) || !ownership.isOwnerRoleBinding(binding)) {
            return;
        }
        if (ownership.isSoleUserOwner(binding.getOrganizationId(), binding.getSubjectId())) {
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
    private void guardLastManagementBindingRetained(RoleBindingEntity target) {

        var rolesById = roleRepository.findByOrganizationId(target.getOrganizationId()).stream()
                .collect(Collectors.toMap(RoleEntity::getId, r -> r));
        var targetRole = rolesById.get(target.getRoleId());
        if (targetRole == null) {
            return;
        }
        var otherBindings = repository.findByOrganizationId(target.getOrganizationId()).stream()
                .filter(a -> !a.getId().equals(target.getId())).toList();
        for (var guard : MANAGEMENT_GUARDS) {
            if (!grants(targetRole, guard.perm())) {
                continue;
            }
            var keptByAnotherBinding = otherBindings.stream().map(a -> rolesById.get(a.getRoleId()))
                    .filter(Objects::nonNull).anyMatch(r -> grants(r, guard.perm()));
            if (!keptByAnotherBinding) {
                throw new ConflictException(guard.lastCode(),
                        "At least one group must keep " + guard.perm().code() + " " + guard.lastReason());
            }
        }
    }

    private boolean grants(RoleEntity role, Permission perm) {

        return role.getPermissions().stream().anyMatch(p -> perm.code().equals(p.getPermission()));
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
    public boolean isSoleOwner(String organizationId, String subject) {

        return ownership.isSoleOwner(organizationId, subject);
    }

    /**
     * Whether the caller holds this Organization's seeded Owner role through any binding the gate matches: their own,
     * or one of their groups'. {@code MembershipService.changeRole} lets an Owner grant {@code ORGANIZER} in a
     * community left with none; an Organization whose Owners hold the role through a directory group (every Self-hosted
     * one, and an enterprise whose ownership moved to its directory) would otherwise have nobody who can.
     */
    @Transactional(readOnly = true)
    public boolean callerHoldsOwnerRole(String organizationId) {

        return ownership.ownerRoleId(organizationId).filter(ownerRoleId -> gate.currentRoleIds().contains(ownerRoleId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<RoleBinding> assigneesOf(Long roleId) {

        gate.requirePermission(Permission.ROLE_READ);
        return repository.findByRoleId(roleId).stream().map(this::toModel).toList();
    }

    private RoleBinding toModel(RoleBindingEntity e) {

        return RoleBinding.builder().id(e.getId()).subjectType(e.getSubjectType()).subjectId(e.getSubjectId())
                .subjectLabel(e.getSubjectLabel()).roleId(e.getRoleId()).build();
    }
}
