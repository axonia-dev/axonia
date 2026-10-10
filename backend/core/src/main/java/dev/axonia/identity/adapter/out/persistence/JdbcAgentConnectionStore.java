package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.AgentConnectionStore;
import dev.axonia.identity.domain.AgentConnection;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcAgentConnectionStore implements AgentConnectionStore {

    private final AgentConnectionRepository agentConnectionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentConnection> findById(Long id) {
        return agentConnectionRepository.findById(id).map(JdbcAgentConnectionStore::toAgentConnection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentConnection> findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc(
            String organizationId, String userId) {
        return agentConnectionRepository
                .findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc(organizationId, userId)
                .stream().map(JdbcAgentConnectionStore::toAgentConnection).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentConnection> findByOrganizationIdAndUserIdAndId(String organizationId, String userId, Long id) {
        return agentConnectionRepository.findByOrganizationIdAndUserIdAndId(organizationId, userId, id)
                .map(JdbcAgentConnectionStore::toAgentConnection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentConnection> findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
            String organizationId, String userId, String clientId) {
        return agentConnectionRepository
                .findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(organizationId,
                        userId, clientId)
                .stream().map(JdbcAgentConnectionStore::toAgentConnection).toList();
    }

    @Override
    @Transactional
    public AgentConnection save(AgentConnection connection) {
        return toAgentConnection(agentConnectionRepository.save(toEntity(connection)));
    }

    @Override
    public void touchLastUsed(String organizationId, Long id, Instant now, Instant staleBefore) {
        agentConnectionRepository.touchLastUsed(organizationId, id, now, staleBefore);
    }

    @Override
    @Transactional
    public int deleteRetired(String organizationId, Instant pendingBefore, Instant retiredBefore) {
        return agentConnectionRepository.deleteRetired(organizationId, pendingBefore, retiredBefore);
    }

    static AgentConnection toAgentConnection(AgentConnectionEntity entity) {
        return AgentConnection.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).clientId(entity.getClientId()).clientName(entity.getClientName())
                .clientHost(entity.getClientHost()).scopes(entity.getScopes()).resource(entity.getResource())
                .redirectUri(entity.getRedirectUri()).codeChallenge(entity.getCodeChallenge())
                .activatedAt(entity.getActivatedAt()).lastUsedAt(entity.getLastUsedAt()).endsAt(entity.getEndsAt())
                .revokedAt(entity.getRevokedAt()).createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static AgentConnectionEntity toEntity(AgentConnection agentConnection) {
        return AgentConnectionEntity.builder().id(agentConnection.id()).organizationId(agentConnection.organizationId())
                .userId(agentConnection.userId()).clientId(agentConnection.clientId())
                .clientName(agentConnection.clientName()).clientHost(agentConnection.clientHost())
                .scopes(agentConnection.scopes()).resource(agentConnection.resource())
                .redirectUri(agentConnection.redirectUri()).codeChallenge(agentConnection.codeChallenge())
                .activatedAt(agentConnection.activatedAt()).lastUsedAt(agentConnection.lastUsedAt())
                .endsAt(agentConnection.endsAt()).revokedAt(agentConnection.revokedAt())
                .createdAt(agentConnection.createdAt()).updatedAt(agentConnection.updatedAt())
                .version(agentConnection.version()).build();
    }
}
