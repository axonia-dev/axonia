package dev.axonia.events;

import java.util.Collection;
import java.util.Map;

public interface UserProfileAccess {

    String displayName(String organizationId, String userId);

    Map<String, String> displayNames(String organizationId, Collection<String> userIds);

    /**
     * Where each named member sits in time, omitting anyone whose clock is unknown.
     *
     * <p>
     * Partial where {@link #displayNames} is total, and the asymmetry is the point: a name has a legitimate neutral
     * stand-in, a clock does not. An absent key means unknown and must never be filled in by the caller - a guessed
     * zone is indistinguishable from a measured one, and reporting a community as spanning a zone nobody sits in is
     * worse than reporting it as spanning one fewer.
     */
    Map<String, String> timeZones(String organizationId, Collection<String> userIds);
}
