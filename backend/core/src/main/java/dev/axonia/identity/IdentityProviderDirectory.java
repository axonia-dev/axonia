package dev.axonia.identity;

import java.util.List;

/**
 * Discovery port for enumerating an IdP's groups within a organization.
 *
 * <p>
 * <strong>Admin discovery only.</strong> This port is intended exclusively for admin UI flows where an operator selects
 * a group to bind to a role. It MUST NOT be consulted on the request-time authorisation path. The
 * {@code AuthorizationGate} resolves a caller's group membership from the JWT {@code groups} claim (via
 * {@code CurrentToken.groups()}) and must continue doing so - using this port for runtime enforcement would introduce a
 * latency-sensitive outbound call on every request.
 */
public interface IdentityProviderDirectory {

    /**
     * Lists the groups available in the organization's identity provider. Results are short-lived cached - do not treat
     * this as live group membership data.
     *
     * @param organizationId
     *            the application organization whose IdP should be queried
     */
    DirectoryListing listGroups(String organizationId);

    /**
     * @param groupId
     *            the <em>bindable key</em> for this group - the exact string that will appear in the JWT {@code groups}
     *            claim at enforcement time. Keycloak: group <strong>name</strong> (mapper uses
     *            {@code full.path=false}). Entra: group <strong>object id</strong> (GUID, what the {@code groups} claim
     *            carries).
     * @param displayName
     *            human-readable group name
     */
    record IdpGroup(String groupId, String displayName) {
    }

    /** Groups discovered for an Organization plus the source that produced them. */
    record DirectoryListing(GroupSource source, List<IdpGroup> groups) {
        public DirectoryListing {
            groups = List.copyOf(groups);
        }

        public static DirectoryListing none() {
            return new DirectoryListing(GroupSource.NONE, List.of());
        }
    }
}
