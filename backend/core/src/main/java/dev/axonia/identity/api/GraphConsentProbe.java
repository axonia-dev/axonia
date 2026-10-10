package dev.axonia.identity.api;

/**
 * Whether a customer's Entra tenant has granted admin consent to axonia's Graph app, and where an administrator grants
 * it. A service this module publishes and implements itself, for any module that reports on a connection's consent.
 *
 * <p>
 * <strong>Never on the request-time authorisation path.</strong>
 */
public interface GraphConsentProbe {

    /** One consent check's verdict. {@code detail} is null when there is nothing an administrator needs to read. */
    record ConsentVerdict(GraphConsent status, String detail) {
    }

    ConsentVerdict check(String aadTenantId);

    /**
     * The Entra admin-consent URL for this tenant, or null when axonia's Graph app is not configured. The
     * {@code adminconsent} endpoint consents to whatever the app registration declares, so no scope parameter is
     * passed.
     */
    String consentUrlFor(String aadTenantId);
}
