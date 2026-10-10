package dev.axonia.agent.application;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;

import dev.axonia.agent.application.AuthorizationOutcome.Ready;
import dev.axonia.agent.application.AuthorizationOutcome.Redirect;
import dev.axonia.agent.application.AuthorizationOutcome.Refused;
import dev.axonia.agent.application.port.out.ClientMetadataDocuments;
import dev.axonia.agent.application.port.out.ClientRedirects;
import dev.axonia.agent.domain.AgentScope;
import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.AuthorizeConnection;
import dev.axonia.identity.api.connection.Pkce;
import dev.axonia.identity.api.pat.MemberAccounts;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/** Validates an MCP authorize request for the signed-in person and turns their answer into the client redirect. */
@Component
@RequiredArgsConstructor
public class McpAuthorization {

    public static final String INVALID_TARGET = "invalid_target";

    private final ClientMetadataDocuments documents;
    private final AgentConnections connections;
    private final AgentAccess access;
    private final MemberAccounts members;
    private final McpEndpoints endpoints;
    private final ClientRedirects redirects;

    public AuthorizationOutcome review(AuthorizationRequest request) {

        return validated(request,
                client -> new Ready(client.clientName(), client.clientHost(), requestedScopes(request.scope())));
    }

    public AuthorizationOutcome approve(AuthorizationRequest request, Set<String> scopes) {

        return validated(request, client -> {
            var granted = known(scopes);
            if (granted.isEmpty()) {
                throw new ValidationException("no_scope", "Select at least one permission.");
            }
            var code = connections.authorize(AuthorizeConnection.builder().userId(CurrentToken.subject())
                    .organizationId(CurrentToken.organization()).clientId(client.clientId())
                    .clientName(client.clientName()).clientHost(client.clientHost()).scopes(granted)
                    .resource(endpoints.resource()).redirectUri(request.redirectUri())
                    .codeChallenge(request.codeChallenge()).build());
            return redirect(request, Map.of("code", code));
        });
    }

    public AuthorizationOutcome deny(AuthorizationRequest request) {

        return validated(request, client -> redirect(request, Map.of("error", OAuth2ErrorCodes.ACCESS_DENIED)));
    }

    /**
     * The caller is checked before the client's document is fetched, so an ineligible caller never causes an outbound
     * request. A refusal never redirects, which is why no check that could redirect runs before the client is known.
     */
    private AuthorizationOutcome validated(AuthorizationRequest request,
            Function<ClientMetadata, AuthorizationOutcome> onValid) {

        if (!callerMayConnect()) {
            return new Refused("NO_AGENT_ACCESS");
        }
        var client = documents.resolve(request.clientId()).orElse(null);
        if (client == null) {
            return new Refused("UNTRUSTED_CLIENT");
        }
        if (!client.accepts(request.redirectUri())) {
            return new Refused("REDIRECT_MISMATCH");
        }
        if (!"code".equals(request.responseType())) {
            return error(request, OAuth2ErrorCodes.UNSUPPORTED_RESPONSE_TYPE, "Only the code flow is supported.");
        }
        if (!Pkce.isChallenge(request.codeChallenge()) || !"S256".equals(request.codeChallengeMethod())) {
            return error(request, OAuth2ErrorCodes.INVALID_REQUEST, "PKCE with S256 is required.");
        }
        if (request.resource() != null && !endpoints.resource().equals(request.resource())) {
            return error(request, INVALID_TARGET, "Unknown resource.");
        }
        return onValid.apply(client);
    }

    private boolean callerMayConnect() {

        return CurrentToken.hasOrganization() && access.isAvailableToCaller()
                && members.isActive(CurrentToken.organization(), CurrentToken.subject());
    }

    private Redirect error(AuthorizationRequest request, String code, String description) {

        var params = new LinkedHashMap<String, String>();
        params.put("error", code);
        params.put("error_description", description);
        return redirect(request, params);
    }

    private Redirect redirect(AuthorizationRequest request, Map<String, String> params) {

        var all = new LinkedHashMap<>(params);
        if (request.state() != null) {
            all.put("state", request.state());
        }
        all.put("iss", endpoints.issuer());
        return new Redirect(redirects.redirectUrl(request.redirectUri(), all));
    }

    private static List<String> requestedScopes(String scope) {

        var requested = scope == null ? Set.<String>of() : known(new LinkedHashSet<>(Arrays.asList(scope.split(" "))));
        return requested.isEmpty() ? List.of(AgentScope.READ.code()) : List.copyOf(requested);
    }

    private static Set<String> known(Set<String> scopes) {

        var known = new LinkedHashSet<String>();
        scopes.stream().filter(AgentScope::isKnown).forEach(known::add);
        return known;
    }
}
