package dev.axonia.agent.adapter.in.web;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.axonia.agent.application.McpAuthorization;
import dev.axonia.agent.application.McpEndpoints;
import dev.axonia.agent.configuration.AgentSecurityConfig;
import dev.axonia.agent.configuration.McpOAuthProperties;
import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.IssuedTokens;
import dev.axonia.identity.api.connection.RedeemCode;
import dev.axonia.identity.api.connection.RefreshConnection;
import dev.axonia.shared.api.web.ClientAddress;
import lombok.RequiredArgsConstructor;

/** RFC 6749 token endpoint and RFC 7009 revocation endpoint for public MCP clients. */
@RestController
@RequiredArgsConstructor
class McpOAuthTokenController {

    private static final String RESOURCE = "resource";

    private final AgentConnections connections;
    private final McpEndpoints endpoints;
    private final AgentSecurityConfig.McpRateLimiters limiters;
    private final McpOAuthProperties properties;

    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn, @JsonProperty("refresh_token") String refreshToken,
            String scope) {
    }

    record OAuthError(String error, @JsonProperty("error_description") String errorDescription) {
    }

    @PostMapping(path = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Object> token(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {

        return limited(request, () -> {
            if (request.getQueryString() != null) {
                return inTheQueryString();
            }
            if (hasRepeats(form)) {
                return error(OAuth2ErrorCodes.INVALID_REQUEST, "A parameter was repeated.");
            }
            var resource = form.getFirst(RESOURCE);
            if (resource != null && !endpoints.resource().equals(resource)) {
                return error(McpAuthorization.INVALID_TARGET, "Unknown resource.");
            }
            var grantType = form.getFirst(OAuth2ParameterNames.GRANT_TYPE);
            if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
                return authorizationCode(form);
            }
            if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
                return refreshToken(form);
            }
            return error(OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE, "Only authorization_code and refresh_token.");
        });
    }

    @PostMapping(path = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Object> revoke(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {

        return limited(request, () -> {
            if (request.getQueryString() != null) {
                return inTheQueryString();
            }
            var token = form.getFirst(OAuth2ParameterNames.TOKEN);
            if (token == null || token.isBlank()) {
                return error(OAuth2ErrorCodes.INVALID_REQUEST, "The token parameter is required.");
            }
            connections.revokeToken(token);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
        });
    }

    private ResponseEntity<Object> authorizationCode(MultiValueMap<String, String> form) {

        var code = form.getFirst(OAuth2ParameterNames.CODE);
        var clientId = form.getFirst(OAuth2ParameterNames.CLIENT_ID);
        var redirectUri = form.getFirst(OAuth2ParameterNames.REDIRECT_URI);
        var verifier = form.getFirst(PkceParameterNames.CODE_VERIFIER);
        if (anyMissing(code, clientId, redirectUri, verifier)) {
            return error(OAuth2ErrorCodes.INVALID_REQUEST,
                    "code, client_id, redirect_uri and code_verifier are required.");
        }
        if (!properties.trustsHostOf(clientId)) {
            return invalidGrant();
        }
        return issued(connections.redeem(new RedeemCode(code, clientId, redirectUri, verifier)));
    }

    private ResponseEntity<Object> refreshToken(MultiValueMap<String, String> form) {

        var refreshToken = form.getFirst(OAuth2ParameterNames.REFRESH_TOKEN);
        var clientId = form.getFirst(OAuth2ParameterNames.CLIENT_ID);
        if (anyMissing(refreshToken, clientId)) {
            return error(OAuth2ErrorCodes.INVALID_REQUEST, "refresh_token and client_id are required.");
        }
        if (!properties.trustsHostOf(clientId)) {
            return invalidGrant();
        }
        return issued(connections.refresh(new RefreshConnection(refreshToken, clientId)));
    }

    private ResponseEntity<Object> issued(Optional<IssuedTokens> tokens) {

        return tokens
                .<ResponseEntity<Object>>map(t -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.PRAGMA, "no-cache").body(new TokenResponse(t.accessToken(), "Bearer",
                                t.expiresIn().toSeconds(), t.refreshToken(), String.join(" ", t.scopes()))))
                .orElseGet(McpOAuthTokenController::invalidGrant);
    }

    private ResponseEntity<Object> limited(HttpServletRequest request, Supplier<ResponseEntity<Object>> handler) {

        var decision = limiters.tokenRequests().tryConsume(ClientAddress.of(request));
        if (!decision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()))
                    .cacheControl(CacheControl.noStore()).build();
        }
        return handler.get();
    }

    /** RFC 6749 section 3.2: a code, verifier or token in a URL would land in every access log on the way. */
    private static ResponseEntity<Object> inTheQueryString() {
        return error(OAuth2ErrorCodes.INVALID_REQUEST, "Parameters belong in the request body.");
    }

    private static ResponseEntity<Object> invalidGrant() {
        return error(OAuth2ErrorCodes.INVALID_GRANT, "The grant is invalid, expired or used.");
    }

    private static ResponseEntity<Object> error(String code, String description) {

        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new OAuthError(code, description));
    }

    private static boolean hasRepeats(MultiValueMap<String, String> form) {
        return form.values().stream().anyMatch(values -> values.size() > 1);
    }

    private static boolean anyMissing(String... values) {
        return Arrays.stream(values).anyMatch(value -> value == null || value.isBlank());
    }
}
