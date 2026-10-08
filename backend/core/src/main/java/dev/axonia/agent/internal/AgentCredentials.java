package dev.axonia.agent.internal;

import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.identity.connection.AgentConnections;
import dev.axonia.identity.pat.PersonalAccessTokens;
import lombok.RequiredArgsConstructor;

/**
 * Resolves an MCP bearer to its principal: {@code axa_} is a connection token, anything else a personal token. A
 * connection whose client's host is no longer trusted does not authenticate, so narrowing the allowlist stops live
 * access tokens as well as refreshes.
 */
@Component
@RequiredArgsConstructor
class AgentCredentials {

    private static final String CONNECTION_PREFIX = "axa_";

    private final PersonalAccessTokens personalTokens;
    private final AgentConnections connections;
    private final McpOAuthProperties properties;

    Optional<AgentPrincipal> authenticate(String bearer) {

        if (bearer.startsWith(CONNECTION_PREFIX)) {
            return connections.authenticate(bearer).filter(p -> properties.trustsHostOf(p.clientId()))
                    .map(AgentPrincipal::of);
        }
        return personalTokens.authenticate(bearer).map(AgentPrincipal::of);
    }

    void recordUsage(AgentPrincipal principal) {

        switch (principal.credential()) {
            case CONNECTION -> connections.recordUsage(principal.organizationId(), principal.credentialId());
            case PERSONAL_TOKEN -> personalTokens.recordUsage(principal.organizationId(), principal.credentialId());
        }
    }
}
