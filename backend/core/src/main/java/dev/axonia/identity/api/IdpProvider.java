package dev.axonia.identity.api;

import java.util.Arrays;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

/**
 * The identity-provider vendor behind an Organization's SSO connection.
 *
 * <p>
 * The catalogue names a vendor rather than a capability. {@link #GENERIC_OIDC} and {@link #GENERIC_SAML} are the honest
 * answer for a connection whose vendor axonia has no dedicated support for: they name the protocol it speaks and claim
 * nothing more. {@link #OKTA} and {@link #GOOGLE} name vendors configured through those same generic fields.
 *
 * <p>
 * Both are accepted and stored, but the console offers no catalogue tile for either, because neither preset survives
 * contact. Google's OIDC discovery document names one {@code issuer} shared by every Google account, personal or
 * Workspace, so presetting it would authenticate any Google account rather than the customer's domain. Okta's issuer
 * varies by host, by authorization server, and by issuer mode, so no single template holds across the deployment shapes
 * customers actually have. A connection can still be honestly recorded as either value - the enum names what a
 * connection actually is, which is a different question from whether a tile should offer it - it is simply never
 * produced by a console flow today.
 *
 * <p>
 * {@link #key()} is the lower-case routing key used by the internal {@code ProviderGroupSource} implementations; the
 * enum name is what is stored in {@code sso_connection.provider}.
 */
@Slf4j
public enum IdpProvider {

    ENTRA("entra"), OKTA("okta"), GOOGLE("google"), GENERIC_OIDC("generic_oidc"), GENERIC_SAML("generic_saml");

    private final String key;

    IdpProvider(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static IdpProvider fromKey(String key) {

        return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown IdP provider key: " + key));
    }

    /**
     * Whether this vendor federates a Microsoft directory, and so has an Entra tenant, a Graph consent and a tenant
     * proof to reason about. Exactly one value answers {@code true}; the rest of the catalogue speaks plain OIDC or
     * SAML and none of those coordinates exist for it.
     */
    public boolean federatesMicrosoftDirectory() {

        return this == ENTRA;
    }

    /**
     * Parses a value read back from {@code sso_connection.provider}. Rows can predate this enum or carry text a past
     * backfill let through, so this never throws: null, blank and unrecognised names degrade to {@link #GENERIC_OIDC}
     * with a warning rather than turning every read of that Organization's connections into a 500. That fallback is the
     * least-claiming value in the catalogue - it federates no Microsoft directory, so no branch anywhere treats a row
     * axonia failed to understand as one it did.
     */
    public static IdpProvider fromStoredName(String storedName) {

        if (storedName == null || storedName.isBlank()) {
            log.warn("Stored IdP provider is null or blank, defaulting to {}", GENERIC_OIDC);
            return GENERIC_OIDC;
        }
        var normalized = storedName.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.name().equals(normalized)).findFirst().orElseGet(() -> {
            log.warn("Unknown stored IdP provider '{}', defaulting to {}", storedName, GENERIC_OIDC);
            return GENERIC_OIDC;
        });
    }
}
