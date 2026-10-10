package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.events.adapter.out.privacy.EventsMemberDataEraser;

public interface CalendarSubscriptionRepository extends CrudRepository<CalendarSubscriptionEntity, Long> {

    /**
     * The one read that runs before any Organization is in scope, since resolving a bearer to its row is what decides
     * which Organization to enter. The {@code calendar_subscription_auth_lookup} policy is what permits it.
     */
    Optional<CalendarSubscriptionEntity> findByTokenHash(String tokenHash);

    Optional<CalendarSubscriptionEntity> findByOrganizationIdAndCommunityIdAndUserId(String organizationId,
            Long communityId, String userId);

    /**
     * The instant is a plain named parameter supplied by the caller's {@code Clock}, never SQL {@code now()}: the
     * application drives time, and SpEL would break the {@code Instant} converter.
     */
    @Modifying
    @Query("""
            UPDATE calendar_subscription SET last_fetched_at = :at, last_fetched_agent = :agent
            WHERE id = :id
            """)
    void stampFetch(@Param("id") Long id, @Param("at") Instant at, @Param("agent") String agent);

    @Modifying
    @Query("DELETE FROM calendar_subscription WHERE organization_id = :organizationId")
    void deleteByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * Returns the row count deleted, unlike {@link #deleteByOrganizationId(String)}: {@link EventsMemberDataEraser}
     * folds it into the erasure receipt, where a purge has no such receipt to fold it into.
     */
    @Modifying
    @Query("DELETE FROM calendar_subscription WHERE organization_id = :organizationId AND user_id = :userId")
    int deleteByOrganizationIdAndUserId(@Param("organizationId") String organizationId, @Param("userId") String userId);
}
