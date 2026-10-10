package dev.axonia.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.ScimTokenStore;
import dev.axonia.identity.application.scim.ScimPrincipal;
import dev.axonia.identity.application.scim.ScimTokens;
import dev.axonia.identity.domain.ScimToken;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.secret.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class ScimTokenAdapter implements ScimTokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ScimTokenStore store;
    private final Clock clock;

    /**
     * Pure read (no usage write — that is recorded after the Organization GUC is set). Rejects revoked or expired
     * tokens so the SCIM filter returns 401 for them.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ScimPrincipal> authenticate(String bearerToken) {
        var hash = TokenHasher.sha256Hex(bearerToken);
        return store.findByTokenHash(hash).filter(this::isActive).map(e -> new ScimPrincipal(e.organizationId()));
    }

    private boolean isActive(ScimToken entity) {
        if (entity.revokedAt() != null) {
            return false;
        }
        return entity.expiresAt() == null || entity.expiresAt().isAfter(clock.instant());
    }

    @Override
    @Transactional
    public String generate(String organizationId, String label, Instant expiresAt) {
        var randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        var plaintext = "scim_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        store.save(ScimToken.builder().organizationId(organizationId).tokenHash(TokenHasher.sha256Hex(plaintext))
                .label(label).expiresAt(expiresAt).build());

        return plaintext;
    }

    @Override
    @Transactional
    public void revoke(String organizationId, Long tokenId) {
        var entity = store.findByOrganizationIdAndId(organizationId, tokenId)
                .orElseThrow(() -> new NotFoundException("SCIM token not found: " + tokenId));
        store.save(entity.toBuilder().revokedAt(clock.instant()).build());
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
            store.touchLastUsed(organizationId, TokenHasher.sha256Hex(bearerToken), clock.instant());
        } catch (RuntimeException e) {
            log.warn("Failed to record SCIM token usage for organization {}", organizationId, e);
        }
    }
}
