package dev.axonia.identity;

/**
 * Whether axonia's multi-tenant Graph app registration holds admin consent in a customer's Entra tenant.
 *
 * <p>
 * Deliberately not part of a connection's health: absent consent degrades directory group discovery and avatar sync
 * while sign-in keeps working, so it must never paint a working connection unhealthy.
 */
public enum GraphConsent {

    /** Consent granted and every required permission present, or the token could not be read for permissions. */
    GRANTED,

    /** Consent granted, but at least one required application permission was not part of the grant. */
    INCOMPLETE,

    /** No service principal in the tenant, so consent was never granted. */
    ABSENT,

    /** Never checked, or the check could not reach a verdict. */
    UNKNOWN;

    /**
     * Parses a value read back from {@code sso_connection.graph_consent}. A connection nobody has checked stores
     * nothing, which is {@link #UNKNOWN}, exactly as a connection's health reads its own column.
     */
    public static GraphConsent storedOrUnknown(String storedName) {

        return storedName == null ? UNKNOWN : valueOf(storedName);
    }
}
