package dev.axonia.recognition.internal;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.recognition.ContributionType;
import dev.axonia.recognition.ReactionPlacementKey;

public interface ReactionRepository extends CrudRepository<ReactionEntity, Long> {

    List<ReactionEntity> findByOrganizationIdAndContributionTypeAndContributionId(String organizationId,
            ContributionType contributionType, Long contributionId);

    List<ReactionEntity> findByOrganizationIdAndReactorUserId(String organizationId, String reactorUserId);

    /**
     * {@code key.contributionType} binds as the enum itself, not its name, the same precedent
     * {@code MembershipRepository#findUserIdsByOrganizationIdAndCommunityIdAndRole} follows for {@code CommunityRole}:
     * Spring Data JDBC's own enum conversion applies to a SpEL record-property parameter exactly as it does to a plain
     * {@code @Query} parameter or a derived finder's, so binding through {@link ReactionPlacementKey} still matches the
     * stored value rather than falling back to string comparison. The six columns this used to bind as separate
     * parameters travel together as one {@code ReactionPlacementKey} instead, to stay under PMD's parameter limit.
     */
    @Modifying
    @Query("""
            DELETE FROM contribution_reaction
             WHERE organization_id = :#{#key.organizationId}
               AND contribution_type = :#{#key.contributionType}
               AND contribution_id = :#{#key.contributionId}
               AND reactor_user_id = :#{#key.reactorUserId}
               AND COALESCE(emoji, '') = COALESCE(:#{#key.emoji}, '')
               AND COALESCE(definition_id, -1) = COALESCE(:#{#key.definitionId}, -1)
            """)
    int deletePlacement(@Param("key") ReactionPlacementKey key);

    @Modifying
    @Query("DELETE FROM contribution_reaction WHERE organization_id = :organizationId "
            + "AND reactor_user_id = :reactorUserId")
    int deleteByReactor(@Param("organizationId") String organizationId, @Param("reactorUserId") String reactorUserId);
}
