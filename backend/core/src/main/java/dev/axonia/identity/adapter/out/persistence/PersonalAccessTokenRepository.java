package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PersonalAccessTokenRepository extends CrudRepository<PersonalAccessTokenEntity, Long> {

    Optional<PersonalAccessTokenEntity> findByTokenHash(String tokenHash);

    List<PersonalAccessTokenEntity> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(String organizationId,
            String userId);

    Optional<PersonalAccessTokenEntity> findByOrganizationIdAndUserIdAndId(String organizationId, String userId,
            Long id);

    /** Writes at most once per {@code staleBefore} window, so a busy agent does not write on every request. */
    @Modifying
    @Query("UPDATE personal_access_token SET last_used_at = :now, updated_at = :now WHERE organization_id = :org "
            + "AND id = :id AND (last_used_at IS NULL OR last_used_at < :staleBefore)")
    void touchLastUsed(@Param("org") String org, @Param("id") Long id, @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore);

    void deleteByOrganizationId(String organizationId);
}
