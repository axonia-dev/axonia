package dev.axonia.community.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.community.CommunityRole;
import dev.axonia.community.MemberCommunity;

public interface MembershipRepository
        extends
            CrudRepository<MembershipEntity, Long>,
            PagingAndSortingRepository<MembershipEntity, Long> {

    Optional<MembershipEntity> findByOrganizationIdAndCommunityIdAndUserId(String organizationId, Long communityId,
            String userId);

    /**
     * Every community the given member belongs to within an Organization. Callers that only know (organizationId,
     * userId) - the account-erasure orchestrator, which acts on an arbitrary member rather than the current caller and
     * so never has a slug to resolve a single community from - use this instead of the slug-scoped finder above.
     */
    List<MembershipEntity> findByOrganizationIdAndUserId(String organizationId, String userId);

    /** The member's communities that are not archived, ordered by name ignoring case. */
    @Query("""
            SELECT c.id, c.slug, c.name
            FROM memberships m
            JOIN communities c ON c.id = m.community_id AND c.organization_id = m.organization_id
            WHERE m.organization_id = :organizationId AND m.user_id = :userId AND c.status = 'ACTIVE'
            ORDER BY lower(c.name), c.id
            """)
    List<MemberCommunity> findActiveCommunitiesOf(@Param("organizationId") String organizationId,
            @Param("userId") String userId);

    long countByOrganizationIdAndCommunityId(String organizationId, Long communityId);

    long countByOrganizationIdAndCommunityIdAndRole(String organizationId, Long communityId, CommunityRole role);

    @Query("""
            SELECT count(*) FROM memberships
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND joined_at >= :from AND joined_at < :to
            """)
    long countJoins(@Param("organizationId") String organizationId, @Param("communityId") Long communityId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * One grouped row per non-empty bucket; {@link BucketCount#bucket()} is clamped into [0, lastBucket].
     *
     * <p>
     * Takes a {@link JoinsPerBucketQuery} rather than four more parameters because PMD refuses a method with five or
     * more (see that record's javadoc for why {@code from}/{@code to} stay outside it).
     *
     * <p>
     * The {@code least(...)} clamp is defensive, not load-bearing: with a whole-days window and the half-open
     * {@code joined_at < :to} predicate, the ratio never reaches {@code lastBucket + 1} in practice, so no known row
     * can force it. It stays so that a bucket index that ever did land out of range is folded into the last bucket
     * instead of silently vanishing when {@link dev.axonia.health.Buckets#zeroFill} builds the series.
     */
    @Query("""
            SELECT least(floor(extract(epoch FROM (joined_at - :from)) / :#{#q.bucketSeconds}), :#{#q.lastBucket})::int
                     AS bucket,
                   count(*) AS n
            FROM memberships
            WHERE organization_id = :#{#q.organizationId} AND community_id = :#{#q.communityId}
              AND joined_at >= :from AND joined_at < :to
            GROUP BY 1
            """)
    List<BucketCount> countJoinsPerBucket(@Param("q") JoinsPerBucketQuery query, @Param("from") Instant from,
            @Param("to") Instant to);

    Page<MembershipEntity> findByOrganizationIdAndCommunityId(String organizationId, Long communityId,
            Pageable pageable);

    /**
     * The roster as bare user ids, to narrow a directory name search to the community's members before its limit
     * applies.
     */
    @Query("SELECT user_id FROM memberships WHERE organization_id = :organizationId AND community_id = :communityId")
    List<String> findUserIdsByOrganizationIdAndCommunityId(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId);

    /**
     * The community's holders of a given role, as bare user ids - {@link CommunityRole#ORGANIZER} is what the
     * awaiting-close nudge sends to, alongside a session's facilitator.
     */
    @Query("""
            SELECT user_id FROM memberships
            WHERE organization_id = :organizationId AND community_id = :communityId AND role = :role
            """)
    List<String> findUserIdsByOrganizationIdAndCommunityIdAndRole(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId, @Param("role") CommunityRole role);

    /** The membership rows of a named set of members, for a listing ordered by something this table does not store. */
    List<MembershipEntity> findByOrganizationIdAndCommunityIdAndUserIdIn(String organizationId, Long communityId,
            Collection<String> userIds);
}
