package dev.axonia.identity.api;

/**
 * Which connected integration produced a directory group listing. {@code NONE} means suggestions are unavailable and
 * the admin must bind by value.
 *
 * <p>
 * There is deliberately no member for the application's own identity provider: it belongs to the deployment, not to any
 * customer, so it is never a directory an Organization may be offered - see {@code RoutingIdentityProviderDirectory}.
 */
public enum GroupSource {
    SCIM, GRAPH, NONE
}
