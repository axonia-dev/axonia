package dev.axonia.identity.connection;

import java.util.Set;

import lombok.Builder;

/** Parameters to record a person's consent to one MCP client and issue the authorization code it redeems. */
@Builder
public record AuthorizeConnection(String userId, String organizationId, String clientId, String clientName,
        String clientHost, Set<String> scopes, String resource, String redirectUri, String codeChallenge) {
}
