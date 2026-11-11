package dev.axonia.identity.api;

import java.util.Collection;
import java.util.List;

import dev.axonia.identity.api.spi.GroupsClaimSource;

public interface DirectoryGroups {

    /** Reconciliation keys (the values the JWT 'groups' claim presents) for the groups a user belongs to. */
    List<String> reconciliationKeysForUser(String organizationId, String userId);

    /** Display names of the groups a user belongs to, as synced from the directory. */
    List<String> groupDisplayNamesForUser(String organizationId, String userId);

    /**
     * The group keys a caller holds, given what their token's {@code groups} claim carries.
     *
     * <p>
     * A claim that lists the person's directory groups stands alone when present: it is always current, whereas the
     * synced directory is only as fresh as the last SCIM push, so consulting it would re-grant a group the person was
     * just removed from for up to roughly 40 minutes.
     *
     * <p>
     * An empty claim is the truth everywhere except an Entra-connected Organization. Past roughly 200 group memberships
     * Entra omits {@code groups} entirely, and nothing here reads its {@code _claim_names} overage markers, which a
     * Keycloak-brokered import drops anyway, so there the synced directory stands in for the absent claim. Elsewhere,
     * substituting it would put a database read on the authorisation path of every groupless caller, hub users
     * included.
     *
     * <p>
     * A claim filled from another claim of the identity provider ({@link GroupsClaimSource}), such as Entra's app
     * roles, says nothing about directory groups, so the synced directory's keys are added to it whatever it carries.
     */
    List<String> groupKeysFor(String organizationId, String userId, List<String> claimed);

    /** The ids of users belonging to any of the given confirmed groups, by reconciliation key. */
    List<String> userIdsInGroups(String organizationId, Collection<String> reconciliationKeys);
}
