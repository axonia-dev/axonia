package dev.axonia.identity.internal;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.axonia.identity.GraphConsent;

/**
 * Mints a Microsoft Graph client-credentials token for one tenant purely to learn whether that tenant has granted
 * axonia admin consent.
 *
 * <p>
 * Every other Graph call gets its token from the Graph client itself, which keeps one per tenant until shortly before
 * it expires. That is exactly what this must not do: a consent check answered from an hour-old token would report the
 * state before the administrator acted, which is the single most likely moment for it to be asked, and only the raw
 * error body of a refused mint names a missing service principal. So nothing here is cached, and nothing here is shared
 * with the Graph client's store.
 *
 * <p>
 * <strong>Never on the request-time authorisation path.</strong>
 */
@Component
class EntraGraphTokenSource {

    private final RestClient loginClient;
    private final EntraGraphProperties properties;

    EntraGraphTokenSource(@Qualifier("entraLoginRestClient") RestClient loginClient, EntraGraphProperties properties) {
        this.loginClient = loginClient;
        this.properties = properties;
    }

    /** One probe's outcome. {@code accessToken} is null unless {@code status} is {@link GraphConsent#GRANTED}. */
    record ProbeResult(GraphConsent status, String accessToken, String detail) {
    }

    ProbeResult probe(String aadTenantId) {

        if (aadTenantId == null || aadTenantId.isBlank()) {
            return new ProbeResult(GraphConsent.UNKNOWN, null, "the connection carries no directory (tenant) ID");
        }
        if (!properties.isConfigured()) {
            return new ProbeResult(GraphConsent.UNKNOWN, null, "axonia's Graph app registration is not configured");
        }
        try {
            return new ProbeResult(GraphConsent.GRANTED, fetchToken(aadTenantId), null);
        } catch (HttpClientErrorException ex) {
            var body = ex.getResponseBodyAsString();
            return new ProbeResult(namesAMissingServicePrincipal(body) ? GraphConsent.ABSENT : GraphConsent.UNKNOWN,
                    null, body);
        } catch (RestClientException ex) {
            return new ProbeResult(GraphConsent.UNKNOWN, null, ex.getMessage());
        }
    }

    private String fetchToken(String aadTenantId) {

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", "https://graph.microsoft.com/.default");

        var tokenBody = loginClient.post().uri("/{aadTenantId}/oauth2/v2.0/token", aadTenantId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (tokenBody == null || tokenBody.get("access_token") == null) {
            throw new IllegalStateException("Entra token endpoint returned no access_token for tenant " + aadTenantId
                    + " - check the connection's aadTenantId, and axonia.idp.entra.client-id and .client-secret");
        }
        return (String) tokenBody.get("access_token");
    }

    /**
     * Admin consent is what creates axonia's service principal in the customer tenant, so Entra's "application not
     * found in the directory" is the one response that proves consent was never granted. Anything else the token
     * endpoint refuses could be a bad axonia credential or a Microsoft outage, and must not be reported as the
     * customer's missing consent.
     */
    private static boolean namesAMissingServicePrincipal(String errorBody) {

        return errorBody != null && errorBody.contains("AADSTS700016");
    }
}
