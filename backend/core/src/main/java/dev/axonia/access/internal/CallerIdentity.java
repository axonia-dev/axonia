package dev.axonia.access.internal;

import java.util.List;

interface CallerIdentity {
    String subject();
    String organizationId();
    List<String> groups();

    /**
     * Whether the caller is an agent acting on a personal access token, rather than a person's own browser session. An
     * agent holds only the permissions bound to it directly, never a group's, so {@link #groups()} is always empty for
     * an agent - this lets a consumer such as {@code DefaultAuthorizationGate} tell that expected, by-design shape
     * apart from a person who genuinely resolved to no groups.
     */
    boolean isAgent();
}
