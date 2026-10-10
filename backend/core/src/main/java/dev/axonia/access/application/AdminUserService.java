package dev.axonia.access.application;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.application.port.out.RoleStore;
import dev.axonia.access.domain.RoleDefinition;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroups;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Read model for the admin Users screen.
 *
 * <p>
 * Each listed user is annotated with their DIRECT (subjectType="USER") effective roles, plus any GROUP-derived roles
 * where the user's directory reconciliation keys match a GROUP binding. Group-derived roles carry a null
 * {@code bindingId} — they cannot be revoked from the detail drawer; the GROUP binding itself would need to be
 * modified.
 *
 * <p>
 * Group display names (the human-readable names of the groups a user belongs to) are surfaced on the {@link #byId}
 * detail path only; the list path passes an empty list to avoid extra queries beyond the per-user reconciliation key
 * lookup that provenance already requires.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AuthorizationGate gate;
    private final OrganizationContext organization;
    private final RoleBindingStore bindingStore;
    private final RoleStore roleStore;
    private final UserDirectory userDirectory;
    private final DirectoryGroups directoryGroups;

    // Known limitation: the org roster is computed by scanning the organization directory (capped). Member is an
    // implicit baseline (anyone in the directory), so the roster is ALL directory members — each annotated with their
    // effective roles where bindings exist. Matches the codebase's modest-roster, in-memory stance; revisit with a
    // dedicated read model if organizations grow large.
    private static final int ROSTER_SCAN_LIMIT = 1000;

    /**
     * One page of the organization's roster, carrying the roster's full size as the page total.
     *
     * <p>
     * The total is counted from the scanned roster rather than from the returned slice: the console shows a "N people"
     * tally beside the list, and a slice length would report the page size instead of the headcount the moment an
     * organization outgrows one page. Both numbers stop at {@link #ROSTER_SCAN_LIMIT} - the known limitation above - so
     * the tally is the roster's size as this read model can see it, not a database-wide count.
     *
     * <p>
     * Only the returned slice is annotated with roles: each row costs a reconciliation-key lookup, so annotating the
     * whole scanned roster to serve one page of it would multiply the queries by the number of pages.
     */
    @Transactional(readOnly = true)
    public Page<AdminUser> list(String query, Pageable pageable) {

        gate.requirePermission(Permission.MEMBER_READ);
        var organizationId = organization.organizationId();

        var allBindings = bindingStore.listIn(organizationId);
        var directByUser = directUserBindings(allBindings);
        var groupBindings = groupBindings(allBindings);
        var roleMaps = roleMapsByOrganization(organizationId);
        var roleNames = roleMaps.names();
        var rolePermissions = roleMaps.permissions();

        var q = query == null ? "" : query;
        // includeDeparted: true - the admin Users console shows who exists, including people who have left.
        var roster = userDirectory.searchByOrganization(UserDirectory.DirectorySearch.builder()
                .organizationId(organizationId).query(q).page(0).size(ROSTER_SCAN_LIMIT).includeDeparted(true).build());
        var content = roster.stream()
                .sorted(java.util.Comparator.comparing(UserDirectory.DirectoryUser::displayName,
                        String.CASE_INSENSITIVE_ORDER))
                .skip(pageable.getOffset()).limit(pageable.getPageSize()).map(u -> {
                    var keys = directoryGroups.reconciliationKeysForUser(organizationId, u.userId());
                    var matchedGroup = groupBindings.stream().filter(a -> keys.contains(a.subjectId())).toList();
                    var effectiveRoles = buildEffectiveRoles(directByUser.getOrDefault(u.userId(), List.of()),
                            matchedGroup, roleNames, rolePermissions);
                    return toAdminUser(u, effectiveRoles, List.of());
                }).toList();
        return new PageImpl<>(content, pageable, roster.size());
    }

    @Transactional(readOnly = true)
    public AdminUser byId(String userId) {

        gate.requirePermission(Permission.MEMBER_READ);
        var organizationId = organization.organizationId();

        // byIds is total: an unknown id comes back as an ERASED entry rather than an empty list, so not-found is
        // filtering that status out, not the absence of a result.
        var user = userDirectory.byIds(organizationId, List.of(userId)).stream()
                .filter(u -> u.status() != UserDirectory.MemberStatus.ERASED).findFirst()
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        var allBindings = bindingStore.listIn(organizationId);
        var directByUser = directUserBindings(allBindings);
        var groupBindings = groupBindings(allBindings);
        var roleMaps = roleMapsByOrganization(organizationId);
        var roleNames = roleMaps.names();
        var rolePermissions = roleMaps.permissions();

        var keys = directoryGroups.reconciliationKeysForUser(organizationId, userId);
        var matchedGroup = groupBindings.stream().filter(a -> keys.contains(a.subjectId())).toList();
        var effectiveRoles = buildEffectiveRoles(directByUser.getOrDefault(userId, List.of()), matchedGroup, roleNames,
                rolePermissions);
        var groups = directoryGroups.groupDisplayNamesForUser(organizationId, userId);

        return toAdminUser(user, effectiveRoles, groups);
    }

    private java.util.Map<String, List<SubjectBinding>> directUserBindings(List<SubjectBinding> allBindings) {
        return allBindings.stream().filter(a -> "USER".equals(a.subjectType()))
                .collect(Collectors.groupingBy(SubjectBinding::subjectId));
    }

    private List<SubjectBinding> groupBindings(List<SubjectBinding> allBindings) {
        return allBindings.stream().filter(a -> "GROUP".equals(a.subjectType())).toList();
    }

    private record RoleMaps(java.util.Map<Long, String> names, java.util.Map<Long, List<String>> permissions) {
    }

    private RoleMaps roleMapsByOrganization(String organizationId) {
        var roles = roleStore.listIn(organizationId);
        var names = roles.stream().collect(Collectors.toMap(r -> r.id(), r -> r.name()));
        var permissions = roles.stream().collect(Collectors.toMap(r -> r.id(), RoleDefinition::permissions));
        return new RoleMaps(names, permissions);
    }

    private List<EffectiveRole> buildEffectiveRoles(List<SubjectBinding> directBindings,
            List<SubjectBinding> groupBindings, java.util.Map<Long, String> roleNames,
            java.util.Map<Long, List<String>> rolePermissions) {
        var direct = directBindings.stream()
                .map(a -> EffectiveRole.builder().role(roleNames.getOrDefault(a.roleId(), "(unknown)")).via("DIRECT")
                        .bindingId(a.id()).roleId(a.roleId())
                        .permissions(rolePermissions.getOrDefault(a.roleId(), List.of())).build())
                .toList();
        // Distinct by role, not by binding: being in two bound groups that both grant Auditor is holding Auditor once.
        // A GROUP entry carries no bindingId, so a second one is indistinguishable from the first - it only rendered
        // the same chip twice under the same React key. DIRECT entries stay separate: same role, different provenance,
        // and only the direct one is revocable from the drawer.
        var group = groupBindings.stream().map(SubjectBinding::roleId).distinct()
                .map(roleId -> EffectiveRole.builder().role(roleNames.getOrDefault(roleId, "(unknown)")).via("GROUP")
                        .bindingId(null).roleId(roleId).permissions(rolePermissions.getOrDefault(roleId, List.of()))
                        .build())
                .toList();
        return Stream.concat(direct.stream(), group.stream()).toList();
    }

    private AdminUser toAdminUser(UserDirectory.DirectoryUser u, List<EffectiveRole> effectiveRoles,
            List<String> groups) {
        return AdminUser.builder().userId(u.userId()).displayName(u.displayName()).email(u.email())
                .effectiveRoles(effectiveRoles).joinedAt(u.joinedAt()).lastActiveAt(u.lastActiveAt()).groups(groups)
                .memberStatus(u.status()).build();
    }
}
