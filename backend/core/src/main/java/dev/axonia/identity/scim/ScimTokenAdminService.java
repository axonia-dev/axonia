package dev.axonia.identity.scim;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.CurrentToken;
import dev.axonia.identity.ScimTokenGate;
import dev.axonia.identity.internal.ScimTokenEntity;
import dev.axonia.identity.internal.ScimTokenRepository;

@Service
public class ScimTokenAdminService {

    private final ScimTokens scimTokens;
    private final ScimTokenGate gate;
    private final ScimTokenRepository tokenRepository;
    private final Clock clock;

    ScimTokenAdminService(ScimTokens scimTokens, ScimTokenGate gate, ScimTokenRepository tokenRepository, Clock clock) {
        this.scimTokens = scimTokens;
        this.gate = gate;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    /**
     * Mints a new labeled SCIM token for the caller's own Organization (additive). Requires {@code scim:write}. This
     * service, like the {@link ScimTokens} port beneath it, still permits a null {@code expiresAt} (never expires); its
     * only caller, {@code ScimTokenAdminController}, requires a present, future value at the HTTP boundary. Returns
     * plaintext ONCE.
     */
    @Transactional
    public String generate(String label, Instant expiresAt) {
        gate.requireWrite();
        return scimTokens.generate(CurrentToken.organization(), label, expiresAt);
    }

    /**
     * Revokes a single SCIM token by id within the caller's own Organization. Requires {@code scim:write}.
     */
    public void revoke(Long tokenId) {
        gate.requireWrite();
        scimTokens.revoke(CurrentToken.organization(), tokenId);
    }

    /**
     * Lists SCIM token metadata for the caller's own Organization. Never exposes the hash/secret. Requires
     * {@code scim:read}.
     */
    @Transactional(readOnly = true)
    public List<ScimTokenView> list() {
        gate.requireRead();
        var organizationId = CurrentToken.organization();
        return tokenRepository.findByOrganizationId(organizationId).stream().map(e -> new ScimTokenView(e.getId(),
                e.getLabel(), e.getLastUsedAt(), e.getExpiresAt(), e.getCreatedAt(), deriveStatus(e))).toList();
    }

    private String deriveStatus(ScimTokenEntity entity) {
        if (entity.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(clock.instant())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }
}
