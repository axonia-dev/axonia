package dev.axonia.access.internal;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.KnownPermissions;
import dev.axonia.identity.DirectoryGroups;
import dev.axonia.identity.PermissionHolders;
import lombok.RequiredArgsConstructor;

/**
 * Answers {@link PermissionHolders#userIdsWith} the way {@link DefaultAuthorizationGate} grants the permission to a
 * single caller in the common case: a subject holds it through either a {@code USER}-typed {@code role_binding} naming
 * them directly, or a {@code GROUP}-typed one naming a confirmed directory group they belong to. Reading only
 * {@code USER} bindings would answer a different question than the gate actually enforces, and would answer "nobody" in
 * exactly the SCIM-managed enterprise tenants a permission-holder lookup exists to serve.
 *
 * <p>
 * <strong>The two do not answer identically.</strong> {@code DefaultAuthorizationGate#matchingBindings} expands a
 * {@code GROUP} binding against the caller's own token claim first, falling back to the SCIM-synced directory only when
 * that claim is empty; this adapter has no caller and no token, so it expands every {@code GROUP} binding through the
 * directory alone. An organization that binds a permission to a group asserted purely in the token, with nothing pushed
 * to {@code directory_group_member}, grants that permission at request time to a member this lookup will never name.
 * That gap is inherent - axonia cannot enumerate the membership of a group that exists only as a claim - so nothing
 * here tries to close it. The one place it is felt is the credential expiry warning: for an organization in that shape,
 * an operator still sees the connection itself, but the warning has nobody to address it to and falls back to the
 * log-only path.
 */
@Component
@RequiredArgsConstructor
class PermissionHoldersAdapter implements PermissionHolders {

    private static final String USER = "USER";
    private static final String GROUP = "GROUP";

    private final RoleBindingRepository roleBindingRepository;
    private final DirectoryGroups directoryGroups;
    private final KnownPermissions knownPermissions;

    /**
     * The {@code USER} and {@code GROUP} halves are each deduplicated in their own query; only the merge of the two
     * needs a Java-side {@code distinct()}, for the subject who holds the permission both directly and through a group.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> userIdsWith(String organizationId, String permissionCode) {

        if (knownPermissions.byCode(permissionCode).isEmpty()) {
            return List.of();
        }

        var direct = roleBindingRepository.findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission(
                organizationId, USER, permissionCode);
        var groupKeys = roleBindingRepository.findDistinctSubjectIdsByOrganizationIdAndSubjectTypeAndPermission(
                organizationId, GROUP, permissionCode);
        var throughGroups = directoryGroups.userIdsInGroups(organizationId, groupKeys);

        return Stream.concat(direct.stream(), throughGroups.stream()).distinct().toList();
    }
}
