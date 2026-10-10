package dev.axonia.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.identity.domain.ScimToken;

/** SCIM bearer credentials, found by digest or by id within an organization. */
public interface ScimTokenStore {

    Optional<ScimToken> findByTokenHash(String tokenHash);

    List<ScimToken> findByOrganizationId(String organizationId);

    Optional<ScimToken> findByOrganizationIdAndId(String organizationId, Long id);

    ScimToken save(ScimToken token);

    void touchLastUsed(String organizationId, String tokenHash, Instant now);
}
