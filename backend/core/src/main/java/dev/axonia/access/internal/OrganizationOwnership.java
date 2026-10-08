package dev.axonia.access.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Who owns an Organization, by this codebase's one definition of "owns".
 *
 * <p>
 * Ownership is held by a <em>direct</em> {@code USER} binding to the seeded "Owner" role and by nothing else. A
 * {@code GROUP} binding to that role never counts: a group is a set of people the directory asserts at sign-in, and an
 * Organization whose only Owner binding names a group can be left with nobody able to sign in as its Owner the moment
 * the directory stops asserting it.
 *
 * <p>
 * Extracted from {@code RoleBindingService} so that the deletion guards and the account-erasure orchestrator's
 * sole-Owner check answer the ownership question from one place. The community repair hatch asks a different question,
 * whether the caller holds the Owner role through any binding, and reads only {@link #ownerRoleId} from here. Ungated
 * on purpose, exactly as {@code AccessSeeder} is: the erasure caller acts across an arbitrary Organization with no HTTP
 * request and no permission of its own.
 */
@Component
@RequiredArgsConstructor
public class OrganizationOwnership {

    private static final String USER = "USER";
    private static final String OWNER_ROLE = "Owner";

    private final RoleRepository roleRepository;
    private final RoleBindingRepository bindingRepository;

    /**
     * Whether {@code subject} is the sole Owner of this Organization: a lone binding to the Owner role, held directly
     * by this subject. Counts every binding to the role regardless of subject type, so a GROUP Owner binding alongside
     * a USER one makes this false - account erasure depends on that GROUP-inclusive meaning.
     */
    @Transactional(readOnly = true)
    public boolean isSoleOwner(String organizationId, String subject) {

        var bindings = ownerBindings(organizationId);
        return bindings.size() == 1 && heldBy(bindings.get(0), subject);
    }

    /**
     * Whether {@code subject} is the sole <em>direct USER</em> Owner, ignoring GROUP Owner bindings entirely. Unlike
     * {@link #isSoleOwner}, a GROUP Owner binding sitting alongside a lone USER one does not hide this: {@link #heldBy}
     * already never counts a GROUP binding as an Owner, so a revoke leaving zero USER Owner bindings leaves the
     * Organization with no Owner by the definition above, whatever GROUP bindings remain.
     */
    @Transactional(readOnly = true)
    public boolean isSoleUserOwner(String organizationId, String subject) {

        var userOwnerBindings = ownerBindings(organizationId).stream().filter(b -> USER.equals(b.getSubjectType()))
                .toList();
        return userOwnerBindings.size() == 1 && userOwnerBindings.get(0).getSubjectId().equals(subject);
    }

    /** Whether {@code binding} is a binding to this Organization's seeded Owner role, not to some other role. */
    @Transactional(readOnly = true)
    public boolean isOwnerRoleBinding(RoleBindingEntity binding) {

        return ownerRoleId(binding.getOrganizationId()).filter(binding.getRoleId()::equals).isPresent();
    }

    private List<RoleBindingEntity> ownerBindings(String organizationId) {

        return ownerRoleId(organizationId).map(bindingRepository::findByRoleId).orElse(List.of());
    }

    /** The id of this Organization's seeded Owner role, empty only when the founding invariant is broken. */
    @Transactional(readOnly = true)
    public Optional<Long> ownerRoleId(String organizationId) {

        return roleRepository.findByOrganizationIdAndName(organizationId, OWNER_ROLE).map(RoleEntity::getId);
    }

    private boolean heldBy(RoleBindingEntity binding, String subject) {

        return USER.equals(binding.getSubjectType()) && binding.getSubjectId().equals(subject);
    }
}
