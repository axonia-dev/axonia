package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.ScimTokenStore;
import dev.axonia.identity.domain.ScimToken;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcScimTokenStore implements ScimTokenStore {

    private final ScimTokenRepository scimTokenRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ScimToken> findByTokenHash(String tokenHash) {
        return scimTokenRepository.findByTokenHash(tokenHash).map(JdbcScimTokenStore::toScimToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScimToken> findByOrganizationId(String organizationId) {
        return scimTokenRepository.findByOrganizationId(organizationId).stream().map(JdbcScimTokenStore::toScimToken)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScimToken> findByOrganizationIdAndId(String organizationId, Long id) {
        return scimTokenRepository.findByOrganizationIdAndId(organizationId, id).map(JdbcScimTokenStore::toScimToken);
    }

    @Override
    @Transactional
    public ScimToken save(ScimToken token) {
        return toScimToken(scimTokenRepository.save(toEntity(token)));
    }

    @Override
    public void touchLastUsed(String organizationId, String tokenHash, Instant now) {
        scimTokenRepository.touchLastUsed(organizationId, tokenHash, now);
    }

    static ScimToken toScimToken(ScimTokenEntity entity) {
        return ScimToken.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .tokenHash(entity.getTokenHash()).label(entity.getLabel()).lastUsedAt(entity.getLastUsedAt())
                .revokedAt(entity.getRevokedAt()).expiresAt(entity.getExpiresAt()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static ScimTokenEntity toEntity(ScimToken scimToken) {
        return ScimTokenEntity.builder().id(scimToken.id()).organizationId(scimToken.organizationId())
                .tokenHash(scimToken.tokenHash()).label(scimToken.label()).lastUsedAt(scimToken.lastUsedAt())
                .revokedAt(scimToken.revokedAt()).expiresAt(scimToken.expiresAt()).createdAt(scimToken.createdAt())
                .updatedAt(scimToken.updatedAt()).version(scimToken.version()).build();
    }
}
