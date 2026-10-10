package dev.axonia.agent.adapter.in.web;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.axonia.agent.application.McpEndpoints;
import dev.axonia.agent.domain.AgentScope;
import lombok.RequiredArgsConstructor;

/** RFC 9728 protected resource metadata and RFC 8414 authorization server metadata for the MCP endpoint. */
@RestController
@RequiredArgsConstructor
class McpOAuthMetadataController {

    private static final CacheControl ONE_HOUR = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final McpEndpoints endpoints;

    record ProtectedResource(String resource, @JsonProperty("authorization_servers") List<String> authorizationServers,
            @JsonProperty("scopes_supported") List<String> scopesSupported,
            @JsonProperty("bearer_methods_supported") List<String> bearerMethodsSupported,
            @JsonProperty("resource_name") String resourceName) {
    }

    record AuthorizationServer(String issuer, @JsonProperty("authorization_endpoint") String authorizationEndpoint,
            @JsonProperty("token_endpoint") String tokenEndpoint,
            @JsonProperty("revocation_endpoint") String revocationEndpoint,
            @JsonProperty("response_types_supported") List<String> responseTypesSupported,
            @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
            @JsonProperty("code_challenge_methods_supported") List<String> codeChallengeMethodsSupported,
            @JsonProperty("token_endpoint_auth_methods_supported") List<String> tokenEndpointAuthMethodsSupported,
            @JsonProperty("revocation_endpoint_auth_methods_supported") List<String> revocationEndpointAuthMethodsSupported,
            @JsonProperty("scopes_supported") List<String> scopesSupported,
            @JsonProperty("client_id_metadata_document_supported") boolean clientIdMetadataDocumentSupported,
            @JsonProperty("authorization_response_iss_parameter_supported") boolean issParameterSupported) {
    }

    @GetMapping({"/.well-known/oauth-protected-resource/mcp", "/.well-known/oauth-protected-resource"})
    ResponseEntity<ProtectedResource> protectedResource() {

        return ResponseEntity.ok().cacheControl(ONE_HOUR).body(new ProtectedResource(endpoints.resource(),
                List.of(endpoints.issuer()), scopes(), List.of("header"), "axonia"));
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    ResponseEntity<AuthorizationServer> authorizationServer() {

        return ResponseEntity.ok().cacheControl(ONE_HOUR)
                .body(new AuthorizationServer(endpoints.issuer(), endpoints.authorization(), endpoints.token(),
                        endpoints.revocation(), List.of("code"), List.of("authorization_code", "refresh_token"),
                        List.of("S256"), List.of("none"), List.of("none"), scopes(), true, true));
    }

    private static List<String> scopes() {
        return List.copyOf(AgentScope.allCodes());
    }
}
