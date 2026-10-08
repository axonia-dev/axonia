package dev.axonia.identity.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.TokenHasher;
import dev.axonia.identity.scim.ScimPrincipal;
import dev.axonia.identity.scim.ScimTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class ScimTokenAdapter implements ScimTokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ScimTokenRepository repository;
    private final Clock clock;

    /**
     * Pure read (no usage write — that is recorded after the Organization GUC is set). Rejects revoked or expired
     * tokens so the SCIM filter returns 401 for them.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ScimPrincipal> authenticate(String bearerToken) {
        var hash = TokenHasher.sha256Hex(bearerToken);
        return repository.findByTokenHash(hash).filter(this::isActive)
                .map(e -> new ScimPrincipal(e.getOrganizationId()));
    }

    private boolean isActive(ScimTokenEntity entity) {
        if (entity.getRevokedAt() != null) {
            return false;
        }
        return entity.getExpiresAt() == null || entity.getExpiresAt().isAfter(clock.instant());
    }

    @Override
    @Transactional
    public String generate(String organizationId, String label, Instant expiresAt) {
        var randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        var plaintext = "scim_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        repository.save(ScimTokenEntity.builder().organizationId(organizationId)
                .tokenHash(TokenHasher.sha256Hex(plaintext)).label(label).expiresAt(expiresAt).build());

        return plaintext;
    }

    @Override
    @Transactional
    public void revoke(String organizationId, Long tokenId) {
        var entity = repository.findByOrganizationIdAndId(organizationId, tokenId)
                .orElseThrow(() -> new NotFoundException("SCIM token not found: " + tokenId));
        repository.save(entity.toBuilder().revokedAt(clock.instant()).build());
    }

    /**
     * Advances {@code last_used_at} for the caller's own token. Runs after the Organization GUC is set, so the
     * {@code scim_token_org_write} RLS policy matches the row. Any failure is logged and swallowed: a usage write must
     * never fail the SCIM request.
     */
    @Override
    @Transactional
    public void recordUsage(String organizationId, String bearerToken) {
        try {
            repository.touchLastUsed(organizationId, TokenHasher.sha256Hex(bearerToken), clock.instant());
        } catch (RuntimeException e) {
            log.warn("Failed to record SCIM token usage for organization {}", organizationId, e);
        }
    }
}
