package dev.axonia.recognition.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ReactionDefinitionRepository extends CrudRepository<ReactionDefinitionEntity, Long> {

    List<ReactionDefinitionEntity> findByOrganizationIdOrderByNameAsc(String organizationId);

    List<ReactionDefinitionEntity> findByOrganizationIdAndStateOrderByNameAsc(String organizationId, String state);

    Optional<ReactionDefinitionEntity> findByOrganizationIdAndId(String organizationId, Long id);

    boolean existsByOrganizationIdAndName(String organizationId, String name);

    int countByOrganizationIdAndState(String organizationId, String state);

    int countByOrganizationIdAndStateAndProposedByUserId(String organizationId, String state, String proposedByUserId);

    /**
     * The proposer's own slice of the queue, backing {@link dev.axonia.recognition.ReactionCatalogService#mine()}. The
     * organization predicate is not optional even though row-level security also carries it: a test connects as the
     * table owner and bypasses policies by ownership, so a finder narrowed to the proposer alone passes green and
     * answers with another tenant's rows in production.
     */
    List<ReactionDefinitionEntity> findByOrganizationIdAndStateAndProposedByUserIdOrderByNameAsc(String organizationId,
            String state, String proposedByUserId);

    /**
     * Backs the administration read only - {@link dev.axonia.recognition.ReactionCatalogService#all()} calls this,
     * {@code approved()} never does. A count query on the picker's path would be a per-page cost for a number no member
     * ever sees.
     * <p>
     * The join carries its own {@code organization_id} predicate. Row-level security and the foreign key already make
     * the count correct without it; what it buys is that the join reads one tenant's slice of the fastest-growing table
     * in the schema rather than scanning all of it on every administration page load.
     */
    @Query("""
            SELECT d.id AS definition_id, COUNT(r.id) AS usage_count
              FROM reaction_definition d
              LEFT JOIN contribution_reaction r ON r.definition_id = d.id
                                               AND r.organization_id = d.organization_id
             WHERE d.organization_id = :organizationId
             GROUP BY d.id
            """)
    List<DefinitionUsage> countUsageByDefinition(@Param("organizationId") String organizationId);

    record DefinitionUsage(Long definitionId, long usageCount) {
    }

    /**
     * A proposal is anonymised, never deleted: once approved, a reaction belongs to the Organization, and removing the
     * hub's vocabulary because one member left would be the wrong party paying for the erasure. Both identity columns
     * are nullable, so nulling them is enough - there is no need for the erasure's surrogate here, unlike a row that
     * must keep naming somebody.
     * <p>
     * The row is reached if either column names the erased member, but each column is only cleared when it is the one
     * that does: a definition somebody else proposed and this member only approved must lose the approval, not the
     * unrelated proposer's identity, and the reverse. Both columns are guarded by their own {@code CASE WHEN} for that
     * reason - an unconditional {@code proposed_by_user_id = NULL} here would erase the proposer's identity on a row
     * this member merely approved, which is exactly the wrong-party mistake this method exists to avoid.
     * <p>
     * {@code version} is bumped for the same reason {@link RecognitionMemberDataEraser}'s own {@code contribution_love}
     * update bumps it: a caller that loaded this row just before the erasure and later calls
     * {@link ReactionDefinitionRepository#save} still carries the pre-erasure identifiers in memory, and an unbumped
     * version would let that stale write resurrect them.
     */
    @Modifying
    @Query("""
            UPDATE reaction_definition
               SET proposed_by_user_id = CASE WHEN proposed_by_user_id = :userId THEN NULL ELSE proposed_by_user_id END,
                   approved_by_user_id = CASE WHEN approved_by_user_id = :userId THEN NULL ELSE approved_by_user_id END,
                   version = version + 1,
                   updated_at = :now
             WHERE organization_id = :organizationId
               AND (proposed_by_user_id = :userId OR approved_by_user_id = :userId)
            """)
    int anonymiseProposer(@Param("organizationId") String organizationId, @Param("userId") String userId,
            @Param("now") Instant now);
}
