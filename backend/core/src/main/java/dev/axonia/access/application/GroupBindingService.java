package dev.axonia.access.application;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.DirectoryGroupsPolicy;
import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.access.application.port.out.RoleBindingStore;
import dev.axonia.access.domain.SubjectBinding;
import dev.axonia.identity.api.DirectoryGroupConfirmation;
import dev.axonia.identity.api.IdentityProviderDirectory;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Discovers IdP groups for the current organization and annotates each group with its existing role bindings. Intended
 * exclusively for admin UI flows - not the authorisation hot-path (see {@link IdentityProviderDirectory} Javadoc).
 */
@Service
@RequiredArgsConstructor
public class GroupBindingService {

    private final IdentityProviderDirectory directory;
    private final RoleBindingStore bindingStore;
    private final AuthorizationGate gate;
    private final OrganizationContext organization;
    private final DirectoryGroupConfirmation directoryGroupConfirmation;
    private final GroupBindingApplicability groupBindingApplicability;

    /**
     * Lists the directory's groups annotated with the role bindings each already carries.
     *
     * <p>
     * Deliberately not {@code @Transactional}: {@link IdentityProviderDirectory#listGroups} is a synchronous Microsoft
     * Graph exchange (an OAuth client-credentials token fetch, then {@code GET /v1.0/groups}) against a tenant this
     * application does not control, and a transaction acquires its pooled connection eagerly at method entry, so
     * annotating this method would pin one connection for the whole Graph round trip, including its timeout when Graph
     * does not answer. The consolidation that would buy is real but modest - roughly four checkouts, once
     * {@code gate.requirePermission} and {@code keysAwaitingConfirmation} are counted, not the "one" a reading of this
     * body alone suggests - and no number of saved checkouts is worth pinning a connection across Graph and its
     * timeout.
     */
    public IdpGroupsResponse list() {

        gate.requirePermission(Permission.ROLE_READ);
        guardDirectoryExists();
        var organizationId = organization.organizationId();
        var listing = directory.listGroups(organizationId);
        var held = directoryGroupConfirmation.keysAwaitingConfirmation(organizationId);
        var groupBindings = bindingStore.listIn(organizationId).stream().filter(e -> "GROUP".equals(e.subjectType()))
                .map(this::toModel).collect(Collectors.groupingBy(RoleBinding::subjectId));

        var discovered = listing.groups().stream().map(g -> new IdpGroupView(g.groupId(), g.displayName(), true,
                held.contains(g.groupId()), groupBindings.getOrDefault(g.groupId(), List.of()))).toList();
        var discoveredIds = listing.groups().stream().map(IdentityProviderDirectory.IdpGroup::groupId)
                .collect(Collectors.toSet());
        var valueAuthored = groupBindings.entrySet().stream().filter(e -> !discoveredIds.contains(e.getKey()))
                .map(e -> new IdpGroupView(e.getKey(), labelFor(e.getValue(), e.getKey()), false,
                        held.contains(e.getKey()), e.getValue()))
                .toList();

        var all = Stream.concat(discovered.stream(), valueAuthored.stream()).toList();
        return new IdpGroupsResponse(listing.source().name(), all);
    }

    /**
     * An Organization with no directory (see {@link DirectoryGroupsPolicy}) has no group listing to serve and no group
     * binding it could annotate - {@code RoleBindingService} refuses to write one. Not found rather than forbidden: its
     * Owner holds {@code role:read} legitimately, for the Roles page, so "you may not" would misname a resource that
     * simply does not exist for this kind of organization.
     */
    private void guardDirectoryExists() {

        if (!groupBindingApplicability.applyIn(organization.organizationId())) {
            throw new NotFoundException("No identity-provider directory exists for a community organization.");
        }
    }

    private static String labelFor(List<RoleBinding> bindings, String fallback) {
        return bindings.stream().map(RoleBinding::subjectLabel).filter(l -> l != null && !l.isBlank()).findFirst()
                .orElse(fallback);
    }

    private RoleBinding toModel(SubjectBinding e) {

        return RoleBinding.builder().id(e.id()).subjectType(e.subjectType()).subjectId(e.subjectId())
                .subjectLabel(e.subjectLabel()).roleId(e.roleId()).build();
    }
}
