package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.pat.MintPersonalAccessToken;
import dev.axonia.identity.api.pat.PersonalAccessTokens;
import dev.axonia.identity.api.pat.PersonalTokenPrincipal;
import dev.axonia.identity.api.pat.PersonalTokenSummary;
import dev.axonia.identity.api.pat.UpdatePersonalAccessToken;
import dev.axonia.identity.application.port.out.PersonalAccessTokenStore;
import dev.axonia.identity.domain.PersonalAccessToken;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.secret.OpaqueSecrets;
import dev.axonia.shared.api.secret.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class PersonalAccessTokenAdapter implements PersonalAccessTokens {

    private static final Duration USAGE_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final PersonalAccessTokenStore store;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonalTokenPrincipal> authenticate(String bearerToken) {
        var hash = TokenHasher.sha256Hex(bearerToken);
        return store.findByTokenHash(hash).filter(this::isActive).map(
                e -> new PersonalTokenPrincipal(e.userId(), e.organizationId(), ScopeList.parse(e.scopes()), e.id()));
    }

    private boolean isActive(PersonalAccessToken entity) {
        if (entity.revokedAt() != null) {
            return false;
        }
        return entity.expiresAt() == null || entity.expiresAt().isAfter(clock.instant());
    }

    @Override
    @Transactional
    public String mint(MintPersonalAccessToken command) {
        var plaintext = OpaqueSecrets.generate("axp_");

        store.save(PersonalAccessToken.builder().userId(command.userId()).organizationId(command.organizationId())
                .tokenHash(TokenHasher.sha256Hex(plaintext)).name(command.name())
                .scopes(ScopeList.join(command.scopes())).expiresAt(command.expiresAt()).build());

        return plaintext;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalTokenSummary> list(String userId, String organizationId) {
        return store.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(organizationId, userId).stream()
                .map(this::toSummary).toList();
    }

    @Override
    @Transactional
    public void revoke(String userId, String organizationId, Long tokenId) {
        var entity = store.findByOrganizationIdAndUserIdAndId(organizationId, userId, tokenId)
                .orElseThrow(() -> new NotFoundException("Personal access token not found: " + tokenId));
        store.save(entity.toBuilder().revokedAt(clock.instant()).build());
    }

    @Override
    @Transactional
    public void update(UpdatePersonalAccessToken command) {
        var entity = store
                .findByOrganizationIdAndUserIdAndId(command.organizationId(), command.userId(), command.tokenId())
                .orElseThrow(() -> new NotFoundException("Personal access token not found: " + command.tokenId()));
        if (!isActive(entity)) {
            throw new ValidationException("token_not_active", "A revoked or expired key cannot be changed.");
        }
        store.save(entity.toBuilder().name(command.name()).scopes(ScopeList.join(command.scopes())).build());
    }

    @Override
    @Transactional
    public void recordUsage(String organizationId, Long tokenId) {
        var now = clock.instant();
        try {
            store.touchLastUsed(organizationId, tokenId, now, now.minus(USAGE_WRITE_INTERVAL));
        } catch (RuntimeException e) {
            log.warn("Failed to record personal token usage for organization {} token {}", organizationId, tokenId, e);
        }
    }

    private PersonalTokenSummary toSummary(PersonalAccessToken e) {
        return new PersonalTokenSummary(e.id(), e.name(), ScopeList.parse(e.scopes()), e.lastUsedAt(), e.expiresAt(),
                e.createdAt(), deriveStatus(e));
    }

    private String deriveStatus(PersonalAccessToken e) {
        if (e.revokedAt() != null) {
            return "REVOKED";
        }
        if (e.expiresAt() != null && !e.expiresAt().isAfter(clock.instant())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }
}
