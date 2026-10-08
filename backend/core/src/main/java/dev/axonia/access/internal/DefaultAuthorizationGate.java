package dev.axonia.access.internal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ForbiddenException;
import dev.axonia.access.AuthorizationGate;
import dev.axonia.access.KnownPermissions;
import dev.axonia.access.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class DefaultAuthorizationGate implements AuthorizationGate {

    private final RoleRepository roleRepository;
    private final RoleBindingRepository bindingRepository;
    private final CallerIdentity caller;
    private final KnownPermissions knownPermissions;

    /**
     * Annotated even though {@link #currentPermissions()} already is, and even though this returns nothing: it calls
     * {@link #hasPermission} on {@code this}, and that in turn calls {@code currentPermissions()} on {@code this}.
     * Spring's proxy-based AOP cannot intercept a self-invocation, so on the path every gated endpoint actually uses -
     * {@code gate.requirePermission(...)} - the annotation three frames down never fired, and each check paid two to
     * four separate connection checkouts: {@code bindingRepository.findByOrganizationId}, whatever
     * {@code caller.groups()} resolves, and {@code roleRepository.findAllById}. The proxy boundary is here, so the
     * annotation has to be here too. {@link #hasPermission} carries its own for callers that enter through it instead.
     */
    @Override
    @Transactional(readOnly = true)
    public void requirePermission(Permission permission) {
        if (!hasPermission(permission)) {
            throw new ForbiddenException("Missing permission: " + permission.code());
        }
    }

    /**
     * See {@link #requirePermission}: {@code currentPermissions()} below is a self-invocation and cannot be relied on
     * to open the transaction.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(Permission permission) {
        return currentPermissions().contains(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Permission> currentPermissions() {
        var roleIds = currentRoleIds();
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return stream(roleRepository.findAllById(roleIds)).flatMap(r -> r.getPermissions().stream())
                .flatMap(rp -> knownPermissions.byCode(rp.getPermission()).stream()).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> currentRoleIds() {
        return matchingBindings().stream().map(RoleBindingEntity::getRoleId).collect(Collectors.toSet());
    }

    private List<RoleBindingEntity> matchingBindings() {
        var userId = caller.subject();
        var groups = caller.groups();
        var organizationId = caller.organizationId();
        var bindings = bindingRepository.findByOrganizationId(organizationId);
        var matching = bindings.stream()
                .filter(a -> ("USER".equals(a.getSubjectType()) && a.getSubjectId().equals(userId))
                        || ("GROUP".equals(a.getSubjectType()) && groups.contains(a.getSubjectId())))
                .toList();
        if (deniedForWantOfGroups(groups, bindings, matching)) {
            if (caller.isAgent()) {
                log.warn("Subject {} in Organization {} matched no role binding while acting as an agent token: "
                        + "an agent holds only the permissions bound to it directly, never a group's, so this "
                        + "denial is expected unless the missing permission should be granted through a USER-typed "
                        + "RoleBinding for this subject.", userId, organizationId);
            } else {
                log.warn("Subject {} in Organization {} matched no role binding: it resolved to no groups from either "
                        + "the token claim or the SCIM-synced directory, and holds no direct binding, while the "
                        + "Organization binds roles to groups. Access will be denied. Check that the bound groups are "
                        + "inside the identity provider's provisioning scope.", userId, organizationId);
            }
        }
        return matching;
    }

    /**
     * Whether the caller is about to be denied with no explanation because their groups never arrived. For a person,
     * the likeliest cause is a role bound to a group outside the customer's SCIM provisioning scope, which therefore
     * never reaches the synced directory that backs the empty-claim supplement; for an agent, an empty {@code groups}
     * claim is not a symptom at all but the intended shape, so the caller distinguishes the two to warn with the right
     * diagnosis.
     *
     * <p>
     * Requires that nothing matched: a caller authorized through a direct {@code USER} binding is not being denied at
     * all, and warning about them turns the ordinary state of every hub member and every bootstrap administrator (an
     * empty groups claim, alongside an Organization that also binds a role to a group) into a per-permission-check
     * warning that says access is denied while access is in fact granted.
     */
    private static boolean deniedForWantOfGroups(List<String> groups, List<RoleBindingEntity> bindings,
            List<RoleBindingEntity> matching) {
        return matching.isEmpty() && groups.isEmpty()
                && bindings.stream().anyMatch(b -> "GROUP".equals(b.getSubjectType()));
    }

    private static <T> Stream<T> stream(Iterable<T> it) {
        return StreamSupport.stream(it.spliterator(), false);
    }
}
