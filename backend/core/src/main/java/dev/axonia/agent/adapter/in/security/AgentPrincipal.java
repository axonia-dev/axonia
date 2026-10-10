package dev.axonia.agent.adapter.in.security;

import java.util.Set;

import dev.axonia.identity.api.connection.ConnectionPrincipal;
import dev.axonia.identity.api.pat.PersonalTokenPrincipal;

/** Who an MCP call acts for, whichever credential it presented. */
record AgentPrincipal(String userId, String organizationId, Set<String> scopes, Credential credential,
        Long credentialId) {

    enum Credential {

        PERSONAL_TOKEN("tokenId", "token"), CONNECTION("connectionId", "connection");

        private final String claim;
        private final String keyPrefix;

        Credential(String claim, String keyPrefix) {
            this.claim = claim;
            this.keyPrefix = keyPrefix;
        }
    }

    static AgentPrincipal of(PersonalTokenPrincipal p) {
        return new AgentPrincipal(p.userId(), p.organizationId(), p.scopes(), Credential.PERSONAL_TOKEN, p.tokenId());
    }

    static AgentPrincipal of(ConnectionPrincipal p) {
        return new AgentPrincipal(p.userId(), p.organizationId(), p.scopes(), Credential.CONNECTION, p.connectionId());
    }

    String claim() {
        return credential.claim;
    }

    String rateLimitKey() {
        return credential.keyPrefix + ":" + credentialId;
    }
}
