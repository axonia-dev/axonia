package dev.axonia.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.identity.domain.PersonalAccessToken;

/** Members' personal access tokens, found by digest or by id within the owner's organization. */
public interface PersonalAccessTokenStore {

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(String organizationId, String userId);

    Optional<PersonalAccessToken> findByOrganizationIdAndUserIdAndId(String organizationId, String userId, Long id);

    PersonalAccessToken save(PersonalAccessToken token);

    /** Writes at most once per {@code staleBefore} window. */
    void touchLastUsed(String organizationId, Long id, Instant now, Instant staleBefore);
}
