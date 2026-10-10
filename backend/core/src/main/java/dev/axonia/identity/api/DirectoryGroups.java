package dev.axonia.identity.api;

import java.util.Collection;
import java.util.List;

public interface DirectoryGroups {

    /** Reconciliation keys (the values the JWT 'groups' claim presents) for the groups a user belongs to. */
    List<String> reconciliationKeysForUser(String organizationId, String userId);

    /** Display names of the groups a user belongs to, as synced from the directory. */
    List<String> groupDisplayNamesForUser(String organizationId, String userId);

    /**
     * The reconciliation keys that stand in for an absent or empty {@code groups} claim, or empty when nothing may
     * substitute for it.
     *
     * <p>
     * Only Microsoft Entra drops the claim silently: past roughly 200 group memberships it omits {@code groups}
     * entirely, and nothing here reads its {@code _claim_names} overage markers, which a Keycloak-brokered import drops
     * anyway. An empty claim from an Entra-connected Organization is therefore ambiguous, and the synced directory is
     * the only way to tell overage from genuine grouplessness.
     *
     * <p>
     * Every other Organization gets an empty list, deliberately: an empty claim there is taken as the truth, and
     * substituting the directory would both re-grant groups to a user just removed from them (until the next SCIM push,
     * up to roughly 40 minutes) and put a database read on the authorisation path of every groupless caller, hub users
     * included.
     */
    List<String> reconciliationKeysSubstitutingForAbsentClaim(String organizationId, String userId);

    /** The ids of users belonging to any of the given confirmed groups, by reconciliation key. */
    List<String> userIdsInGroups(String organizationId, Collection<String> reconciliationKeys);
}
