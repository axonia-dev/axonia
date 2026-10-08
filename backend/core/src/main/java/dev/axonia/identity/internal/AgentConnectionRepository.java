package dev.axonia.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface AgentConnectionRepository extends CrudRepository<AgentConnectionEntity, Long> {

    List<AgentConnectionEntity> findByOrganizationIdAndUserIdAndActivatedAtIsNotNullOrderByActivatedAtDesc(
            String organizationId, String userId);

    Optional<AgentConnectionEntity> findByOrganizationIdAndUserIdAndId(String organizationId, String userId, Long id);

    List<AgentConnectionEntity> findByOrganizationIdAndUserIdAndClientIdAndActivatedAtIsNotNullAndRevokedAtIsNull(
            String organizationId, String userId, String clientId);

    /** Writes at most once per {@code staleBefore} window, so a busy agent does not write on every request. */
    @Modifying
    @Query("UPDATE agent_connection SET last_used_at = :now, updated_at = :now WHERE organization_id = :org "
            + "AND id = :id AND (last_used_at IS NULL OR last_used_at < :staleBefore)")
    void touchLastUsed(@Param("org") String org, @Param("id") Long id, @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore);

    @Modifying
    @Query("DELETE FROM agent_connection WHERE organization_id = :org AND ("
            + "(activated_at IS NULL AND created_at < :pendingBefore) "
            + "OR revoked_at < :retiredBefore OR ends_at < :retiredBefore)")
    int deleteRetired(@Param("org") String org, @Param("pendingBefore") Instant pendingBefore,
            @Param("retiredBefore") Instant retiredBefore);
}
