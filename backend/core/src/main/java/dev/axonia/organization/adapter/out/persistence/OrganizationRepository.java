package dev.axonia.organization.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends CrudRepository<OrganizationEntity, String> {

    /**
     * Toggles the organization-wide kill switch for outbound member messaging. Stamps {@code updated_at} itself (from
     * the caller's injected {@link java.time.Clock}, never SQL {@code now()}) because this bypasses the
     * {@code @LastModifiedDate} auditing that a {@code save} would otherwise apply. Returns the affected row count so
     * the caller can fail loudly on a stale id rather than silently no-op.
     */
    @Modifying
    @Query("UPDATE organization SET communications_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    int updateCommunicationsEnabled(@Param("id") String id, @Param("enabled") boolean enabled,
            @Param("updatedAt") Instant updatedAt);
}
