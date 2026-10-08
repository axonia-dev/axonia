package dev.axonia.identity;

/**
 * An Organization's resolved identity-provider coordinates: which vendor brokers its logins, and for Entra, which
 * customer tenant its Microsoft Graph calls must target and whether they may be made at all.
 *
 * <p>
 * {@code graphReadsAllowed} carries whether the Organization has proved it owns that tenant alongside the tenant rather
 * than leaving each Graph call site to look it up, for the same reason the tenant itself is carried here: the routing
 * layer resolves the Organization's connection once, so a second lookup downstream could disagree with the decision
 * made there.
 */
public record OrganizationIdp(IdpProvider provider, String aadTenantId, boolean graphReadsAllowed) {
}
