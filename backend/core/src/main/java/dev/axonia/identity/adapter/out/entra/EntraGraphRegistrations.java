package dev.axonia.identity.adapter.out.entra;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.util.UriComponentsBuilder;

import dev.axonia.identity.configuration.EntraGraphProperties;

/**
 * axonia's Microsoft Graph client-credentials registration for one customer tenant, built when a call names it. The
 * token endpoint is per tenant, so there is one registration per tenant, {@code entra:<aadTenantId>}, and every Graph
 * call names the tenant it acts for through {@link #actingFor}.
 *
 * <p>
 * The token store keys its entries by registration id, which carries the tenant, so a token minted for one customer's
 * tenant is never handed to a call made for another. Nothing is cached here: the store rebuilds each stored client from
 * the registration this answers now.
 *
 * <p>
 * With no Graph app registration configured there is no registration at all, and a call naming one fails before
 * anything leaves the process.
 */
public final class EntraGraphRegistrations implements ClientRegistrationRepository {

    private static final String PREFIX = "entra:";
    private static final String TOKEN_URI = "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token";
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";

    private final EntraGraphProperties properties;

    public EntraGraphRegistrations(EntraGraphProperties properties) {
        this.properties = properties;
    }

    /** The request attribute naming the registration of {@code aadTenantId}, for {@code RestClient.attributes}. */
    static Consumer<Map<String, Object>> actingFor(String aadTenantId) {
        if (aadTenantId == null || aadTenantId.isBlank()) {
            throw new IllegalStateException("Cannot mint a Microsoft Graph token without an aadTenantId - the "
                    + "Organization's SSO connection is ENTRA but carries no tenant; set its aadTenantId");
        }
        return RequestAttributeClientRegistrationIdResolver.clientRegistrationId(registrationIdOf(aadTenantId));
    }

    /** The id of {@code aadTenantId}'s registration, under which its token is kept. */
    static String registrationIdOf(String aadTenantId) {
        return PREFIX + aadTenantId;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (!properties.isConfigured() || !registrationId.startsWith(PREFIX)) {
            return null;
        }
        var tenant = registrationId.substring(PREFIX.length());
        return ClientRegistration.withRegistrationId(registrationId).clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope(GRAPH_SCOPE)
                .tokenUri(UriComponentsBuilder.fromUriString(TOKEN_URI).buildAndExpand(tenant).toUriString()).build();
    }
}
