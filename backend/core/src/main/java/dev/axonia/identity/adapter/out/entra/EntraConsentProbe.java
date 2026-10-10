package dev.axonia.identity.adapter.out.entra;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.axonia.identity.api.GraphConsent;
import dev.axonia.identity.api.GraphConsentProbe;
import dev.axonia.identity.configuration.EntraGraphProperties;
import dev.axonia.shared.api.http.ClientCredentialsAuthorization;
import tools.jackson.databind.json.JsonMapper;

/**
 * Whether a customer's Entra tenant has granted admin consent to axonia's Graph app, and whether that grant covers
 * every application permission axonia needs.
 *
 * <p>
 * A mint alone answers only the first half: Entra issues app-only tokens with no {@code roles} claim when the service
 * principal exists but no application permissions were granted, which is exactly the state of a tenant that consented
 * before {@code ProfilePhoto.Read.All} was added. The granted permissions are carried in {@code roles}, so the token is
 * read for them.
 *
 * <p>
 * Microsoft's stated position is that Graph access tokens are opaque to clients. That is why an undecodable token
 * degrades to {@link GraphConsent#GRANTED}: the mint succeeded, so the service principal provably exists, and a format
 * change at Microsoft must never make axonia invent a permissions failure that sends a customer's global administrator
 * to re-consent for nothing.
 *
 * <p>
 * A GRANTED verdict drops the tenant's Graph token on this instance; another instance keeps its own until it renews.
 * That token may predate the consent this check has just observed, and Graph answers a token lacking a permission with
 * 403, which keeps it, so an administrator who re-consented to add one would otherwise wait out the rest of its hour.
 *
 * <p>
 * <strong>Never on the request-time authorisation path.</strong>
 */
@Component
public class EntraConsentProbe implements GraphConsentProbe {

    static final Set<String> REQUIRED_PERMISSIONS = Set.of("GroupMember.Read.All", "ProfilePhoto.Read.All");

    private final EntraGraphTokenSource tokenSource;
    private final EntraGraphProperties graphProperties;
    private final JsonMapper jsonMapper;
    private final ClientCredentialsAuthorization graphTokens;

    EntraConsentProbe(EntraGraphTokenSource tokenSource, EntraGraphProperties graphProperties, JsonMapper jsonMapper,
            @Qualifier("entraGraphAuthorization") ClientCredentialsAuthorization graphTokens) {
        this.tokenSource = tokenSource;
        this.graphProperties = graphProperties;
        this.jsonMapper = jsonMapper;
        this.graphTokens = graphTokens;
    }

    @Override
    public ConsentVerdict check(String aadTenantId) {

        var verdict = verdictOf(tokenSource.probe(aadTenantId));
        if (verdict.status() == GraphConsent.GRANTED) {
            graphTokens.forget(EntraGraphRegistrations.registrationIdOf(aadTenantId));
        }
        return verdict;
    }

    private ConsentVerdict verdictOf(EntraGraphTokenSource.ProbeResult probe) {

        if (probe.status() != GraphConsent.GRANTED) {
            return new ConsentVerdict(probe.status(), probe.detail());
        }
        var granted = grantedPermissions(probe.accessToken());
        if (granted.isEmpty()) {
            return new ConsentVerdict(GraphConsent.GRANTED, "consent is granted; permissions could not be read");
        }
        var missing = REQUIRED_PERMISSIONS.stream().filter(permission -> !granted.get().contains(permission)).sorted()
                .toList();
        return missing.isEmpty()
                ? new ConsentVerdict(GraphConsent.GRANTED, null)
                : new ConsentVerdict(GraphConsent.INCOMPLETE, "not granted: " + String.join(", ", missing));
    }

    @Override
    public String consentUrlFor(String aadTenantId) {

        if (!graphProperties.isConfigured()) {
            return null;
        }
        return "https://login.microsoftonline.com/" + aadTenantId + "/adminconsent?client_id="
                + graphProperties.clientId();
    }

    /**
     * Empty means "could not be read", which is not the same as an empty grant. {@code accessToken} is never null here:
     * {@link EntraGraphTokenSource.ProbeResult} only carries a null token when its status is not
     * {@link GraphConsent#GRANTED}, and {@link #verdictOf} returns before reaching this method in that case.
     */
    private Optional<Set<String>> grantedPermissions(String accessToken) {

        var segments = accessToken.split("\\.");
        if (segments.length < 2) {
            return Optional.empty();
        }
        try {
            var payload = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
            var roles = jsonMapper.readTree(payload).get("roles");
            if (roles == null || !roles.isArray()) {
                return Optional.of(Set.of());
            }
            return Optional.of(roles.valueStream().map(node -> node.asString()).collect(Collectors.toSet()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
