package dev.axonia.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;

import dev.axonia.identity.domain.AgentConnectionToken;

/** The secrets of agent connections, held by digest. */
public interface AgentConnectionTokenStore {

    Optional<AgentConnectionToken> findByTokenHash(String tokenHash);

    AgentConnectionToken save(AgentConnectionToken token);

    /** Marks a code or refresh token used. Answers 0 when it already was, which is how single use is enforced. */
    int consume(Long id, Instant now);

    void deleteAccessTokens(Long connectionId);

    int deleteExpired(String organizationId, Instant now);
}
