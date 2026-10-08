package dev.axonia.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.OpaqueSecrets;
import dev.axonia.TokenHasher;
import dev.axonia.ValidationException;
import dev.axonia.identity.pat.MintPersonalAccessToken;
import dev.axonia.identity.pat.PersonalAccessTokens;
import dev.axonia.identity.pat.PersonalTokenPrincipal;
import dev.axonia.identity.pat.PersonalTokenSummary;
import dev.axonia.identity.pat.UpdatePersonalAccessToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class PersonalAccessTokenAdapter implements PersonalAccessTokens {

    private static final Duration USAGE_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final PersonalAccessTokenRepository repository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonalTokenPrincipal> authenticate(String bearerToken) {
        var hash = TokenHasher.sha256Hex(bearerToken);
        return repository.findByTokenHash(hash).filter(this::isActive)
                .map(e -> new PersonalTokenPrincipal(e.getUserId(), e.getOrganizationId(),
                        ScopeList.parse(e.getScopes()), e.getId()));
    }

    private boolean isActive(PersonalAccessTokenEntity entity) {
        if (entity.getRevokedAt() != null) {
            return false;
        }
        return entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(clock.instant());
    }

    @Override
    @Transactional
    public String mint(MintPersonalAccessToken command) {
        var plaintext = OpaqueSecrets.generate("axp_");

        repository.save(PersonalAccessTokenEntity.builder().userId(command.userId())
                .organizationId(command.organizationId()).tokenHash(TokenHasher.sha256Hex(plaintext))
                .name(command.name()).scopes(ScopeList.join(command.scopes())).expiresAt(command.expiresAt()).build());

        return plaintext;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalTokenSummary> list(String userId, String organizationId) {
        return repository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(organizationId, userId).stream()
                .map(this::toSummary).toList();
    }

    @Override
    @Transactional
    public void revoke(String userId, String organizationId, Long tokenId) {
        var entity = repository.findByOrganizationIdAndUserIdAndId(organizationId, userId, tokenId)
                .orElseThrow(() -> new NotFoundException("Personal access token not found: " + tokenId));
        repository.save(entity.toBuilder().revokedAt(clock.instant()).build());
    }

    @Override
    @Transactional
    public void update(UpdatePersonalAccessToken command) {
        var entity = repository
                .findByOrganizationIdAndUserIdAndId(command.organizationId(), command.userId(), command.tokenId())
                .orElseThrow(() -> new NotFoundException("Personal access token not found: " + command.tokenId()));
        if (!isActive(entity)) {
            throw new ValidationException("token_not_active", "A revoked or expired key cannot be changed.");
        }
        repository.save(entity.toBuilder().name(command.name()).scopes(ScopeList.join(command.scopes())).build());
    }

    @Override
    @Transactional
    public void recordUsage(String organizationId, Long tokenId) {
        var now = clock.instant();
        try {
            repository.touchLastUsed(organizationId, tokenId, now, now.minus(USAGE_WRITE_INTERVAL));
        } catch (RuntimeException e) {
            log.warn("Failed to record personal token usage for organization {} token {}", organizationId, tokenId, e);
        }
    }

    private PersonalTokenSummary toSummary(PersonalAccessTokenEntity e) {
        return new PersonalTokenSummary(e.getId(), e.getName(), ScopeList.parse(e.getScopes()), e.getLastUsedAt(),
                e.getExpiresAt(), e.getCreatedAt(), deriveStatus(e));
    }

    private String deriveStatus(PersonalAccessTokenEntity e) {
        if (e.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (e.getExpiresAt() != null && !e.getExpiresAt().isAfter(clock.instant())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }
}
