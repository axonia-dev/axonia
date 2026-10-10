package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.PersonalAccessTokenStore;
import dev.axonia.identity.domain.PersonalAccessToken;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcPersonalAccessTokenStore implements PersonalAccessTokenStore {

    private final PersonalAccessTokenRepository personalAccessTokenRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonalAccessToken> findByTokenHash(String tokenHash) {
        return personalAccessTokenRepository.findByTokenHash(tokenHash)
                .map(JdbcPersonalAccessTokenStore::toPersonalAccessToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalAccessToken> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(String organizationId,
            String userId) {
        return personalAccessTokenRepository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(organizationId, userId)
                .stream().map(JdbcPersonalAccessTokenStore::toPersonalAccessToken).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonalAccessToken> findByOrganizationIdAndUserIdAndId(String organizationId, String userId,
            Long id) {
        return personalAccessTokenRepository.findByOrganizationIdAndUserIdAndId(organizationId, userId, id)
                .map(JdbcPersonalAccessTokenStore::toPersonalAccessToken);
    }

    @Override
    @Transactional
    public PersonalAccessToken save(PersonalAccessToken token) {
        return toPersonalAccessToken(personalAccessTokenRepository.save(toEntity(token)));
    }

    @Override
    public void touchLastUsed(String organizationId, Long id, Instant now, Instant staleBefore) {
        personalAccessTokenRepository.touchLastUsed(organizationId, id, now, staleBefore);
    }

    static PersonalAccessToken toPersonalAccessToken(PersonalAccessTokenEntity entity) {
        return PersonalAccessToken.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .userId(entity.getUserId()).tokenHash(entity.getTokenHash()).name(entity.getName())
                .scopes(entity.getScopes()).lastUsedAt(entity.getLastUsedAt()).revokedAt(entity.getRevokedAt())
                .expiresAt(entity.getExpiresAt()).createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static PersonalAccessTokenEntity toEntity(PersonalAccessToken personalAccessToken) {
        return PersonalAccessTokenEntity.builder().id(personalAccessToken.id())
                .organizationId(personalAccessToken.organizationId()).userId(personalAccessToken.userId())
                .tokenHash(personalAccessToken.tokenHash()).name(personalAccessToken.name())
                .scopes(personalAccessToken.scopes()).lastUsedAt(personalAccessToken.lastUsedAt())
                .revokedAt(personalAccessToken.revokedAt()).expiresAt(personalAccessToken.expiresAt())
                .createdAt(personalAccessToken.createdAt()).updatedAt(personalAccessToken.updatedAt())
                .version(personalAccessToken.version()).build();
    }
}
