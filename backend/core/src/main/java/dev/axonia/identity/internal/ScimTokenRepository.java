package dev.axonia.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ScimTokenRepository extends CrudRepository<ScimTokenEntity, Long> {

    Optional<ScimTokenEntity> findByTokenHash(String tokenHash);

    List<ScimTokenEntity> findByOrganizationId(String organizationId);

    Optional<ScimTokenEntity> findByOrganizationIdAndId(String organizationId, Long id);

    @Modifying
    @Query("UPDATE scim_token SET last_used_at = :now, updated_at = :now WHERE organization_id = :org AND token_hash = :hash")
    void touchLastUsed(@Param("org") String org, @Param("hash") String hash, @Param("now") Instant now);

    void deleteByOrganizationId(String organizationId);
}
