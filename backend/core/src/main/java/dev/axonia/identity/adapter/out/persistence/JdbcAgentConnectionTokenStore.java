package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.AgentConnectionTokenStore;
import dev.axonia.identity.domain.AgentConnectionToken;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcAgentConnectionTokenStore implements AgentConnectionTokenStore {

    private final AgentConnectionTokenRepository agentConnectionTokenRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentConnectionToken> findByTokenHash(String tokenHash) {
        return agentConnectionTokenRepository.findByTokenHash(tokenHash)
                .map(JdbcAgentConnectionTokenStore::toAgentConnectionToken);
    }

    @Override
    @Transactional
    public AgentConnectionToken save(AgentConnectionToken token) {
        return toAgentConnectionToken(agentConnectionTokenRepository.save(toEntity(token)));
    }

    @Override
    @Transactional
    public int consume(Long id, Instant now) {
        return agentConnectionTokenRepository.consume(id, now);
    }

    @Override
    public void deleteAccessTokens(Long connectionId) {
        agentConnectionTokenRepository.deleteAccessTokens(connectionId);
    }

    @Override
    @Transactional
    public int deleteExpired(String organizationId, Instant now) {
        return agentConnectionTokenRepository.deleteExpired(organizationId, now);
    }

    static AgentConnectionToken toAgentConnectionToken(AgentConnectionTokenEntity entity) {
        return AgentConnectionToken.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .connectionId(entity.getConnectionId()).kind(entity.getKind()).tokenHash(entity.getTokenHash())
                .expiresAt(entity.getExpiresAt()).consumedAt(entity.getConsumedAt()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static AgentConnectionTokenEntity toEntity(AgentConnectionToken agentConnectionToken) {
        return AgentConnectionTokenEntity.builder().id(agentConnectionToken.id())
                .organizationId(agentConnectionToken.organizationId()).connectionId(agentConnectionToken.connectionId())
                .kind(agentConnectionToken.kind()).tokenHash(agentConnectionToken.tokenHash())
                .expiresAt(agentConnectionToken.expiresAt()).consumedAt(agentConnectionToken.consumedAt())
                .createdAt(agentConnectionToken.createdAt()).updatedAt(agentConnectionToken.updatedAt())
                .version(agentConnectionToken.version()).build();
    }
}
