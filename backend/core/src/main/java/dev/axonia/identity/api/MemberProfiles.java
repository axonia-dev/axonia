package dev.axonia.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** How members of an organization are named, pictured and placed in time, for any surface that renders a person. */
public interface MemberProfiles {

    /**
     * Batch resolve for display, scoped to the caller's organization. Total: an id that resolves to no row comes back
     * as an {@link UserDirectory.MemberStatus#ERASED} entry under the neutral label. Never carries an email.
     */
    List<DirectoryUser> resolve(Collection<String> ids);

    /** Display name for a specific user in a given organization; empty if not found. */
    Optional<String> displayName(String organizationId, String userId);

    /** Batch display-name lookup for users in a given organization; unknown ids are omitted from the result. */
    Map<String, String> displayNames(String organizationId, Collection<String> userIds);

    /** The effective clock of each named member, omitting anyone who has none. */
    Map<String, String> timeZones(String organizationId, Collection<String> userIds);
}
