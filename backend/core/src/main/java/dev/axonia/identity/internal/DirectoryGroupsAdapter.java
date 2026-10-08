package dev.axonia.identity.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.DirectoryGroups;
import dev.axonia.identity.IdpProvider;
import dev.axonia.identity.OrganizationIdp;
import dev.axonia.identity.OrganizationIdpResolver;
import lombok.RequiredArgsConstructor;

/**
 * Reads synced directory data (groups and memberships written by SCIM provisioning) to satisfy the
 * {@link DirectoryGroups} port.
 */
@Component
@RequiredArgsConstructor
class DirectoryGroupsAdapter implements DirectoryGroups {

    private final UserRepository userRepository;
    private final DirectoryGroupRepository directoryGroupRepository;
    private final DirectoryGroupMemberRepository directoryGroupMemberRepository;
    private final OrganizationIdpResolver organizationIdpResolver;

    /**
     * A group held unconfirmed contributes nothing. Its key is one a role binding was already waiting on when the
     * directory pushed it, so returning it here would grant that binding's role before an administrator confirms the
     * group. Only {@code reconciliation_key} is filtered, not {@link #groupDisplayNamesForUser}, because a display name
     * grants nothing and hiding it would make a held group invisible to the very administrator who has to decide about
     * it.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> reconciliationKeysForUser(String organizationId, String userId) {

        return groupsForUser(organizationId, userId).stream().filter(group -> group.getConfirmedAt() != null)
                .map(DirectoryGroupEntity::getReconciliationKey).filter(key -> key != null).distinct().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> groupDisplayNamesForUser(String organizationId, String userId) {

        return groupsForUser(organizationId, userId).stream().map(DirectoryGroupEntity::getDisplayName)
                .filter(name -> name != null).distinct().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> reconciliationKeysSubstitutingForAbsentClaim(String organizationId, String userId) {

        if (!isEntraConnected(organizationId)) {
            return List.of();
        }
        return reconciliationKeysForUser(organizationId, userId);
    }

    /**
     * Same confirmed-only rule {@link #reconciliationKeysForUser} applies, honoured here in Java rather than in SQL so
     * that a key landing on a held group is provably excluded rather than merely untested: a group held unconfirmed
     * confers none of the roles bound to its key. The member lookup that follows filters
     * {@code identity_user.user_id IS NOT NULL} in
     * {@link DirectoryGroupMemberRepository#findUserIdsByOrganizationIdAndGroupIdIn}, the same "never signed in, nobody
     * to address" rule {@link #groupsForUser} applies to the reverse lookup.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> userIdsInGroups(String organizationId, Collection<String> reconciliationKeys) {

        if (reconciliationKeys.isEmpty()) {
            return List.of();
        }
        var confirmedGroupIds = directoryGroupRepository
                .findByOrganizationIdAndReconciliationKeyIn(organizationId, reconciliationKeys).stream()
                .filter(group -> group.getConfirmedAt() != null).map(DirectoryGroupEntity::getId).toList();
        if (confirmedGroupIds.isEmpty()) {
            return List.of();
        }
        return directoryGroupMemberRepository.findUserIdsByOrganizationIdAndGroupIdIn(organizationId,
                confirmedGroupIds);
    }

    private boolean isEntraConnected(String organizationId) {

        return organizationIdpResolver.resolve(organizationId).map(OrganizationIdp::provider)
                .filter(IdpProvider::federatesMicrosoftDirectory).isPresent();
    }

    /**
     * Resolves the synced directory groups that a user belongs to; empty when the user is unknown, has no
     * {@code userId} yet, or simply holds no membership row.
     *
     * <p>
     * A SCIM-provisioned user that has never logged in has a null {@code userId} (its sub attaches on first login).
     * Looking that up by a null id would resolve to {@code user_id IS NULL} and match <em>every</em> sub-less row, so
     * short-circuit: an account with no sub has not authenticated and carries no reconciled directory groups.
     *
     * <p>
     * Nothing else is guarded, and in particular not {@code arrival}: how a person arrived is a historical fact, never
     * a current-state predicate. Someone created by their own first sign-in is discoverable over SCIM and gets linked
     * into synced groups afterwards, and refusing them here would deny every role bound to those groups.
     */
    private List<DirectoryGroupEntity> groupsForUser(String organizationId, String userId) {

        if (userId == null) {
            return List.of();
        }
        return userRepository.findByOrganizationIdAndUserId(organizationId, userId).map(
                user -> directoryGroupRepository.findByOrganizationIdAndMemberScimId(organizationId, user.getScimId()))
                .orElseGet(List::of);
    }
}
