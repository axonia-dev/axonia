package dev.axonia.events.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.events.CheckpointOutcome;

public interface ActionItemCheckpointRepository extends CrudRepository<ActionItemCheckpointEntity, Long> {

    /**
     * The decisions logged AT this session - the wrap-up's own "What was checked" section, alongside
     * {@link ActionItemRepository#findByOriginSessionId} for what the session raised. {@code communityId} is checked
     * against the session's own {@code community_session.community_id} via the join below, the same defensive posture
     * {@link ActionItemRepository#findByOriginSessionId} takes. A row's own {@code session_id} is never caller-supplied
     * - {@link ActionItemLifecycle#decide} stamps it from the session actually being closed - but a read's safety
     * should not rest solely on an invariant another class maintains, especially when a sibling finder in this exact
     * area already checks the community explicitly rather than trust the session id alone. Ordered by decision order,
     * the sequence an organizer actually worked through the follow-up pass in.
     */
    @Query("""
            SELECT c.* FROM action_item_checkpoint c
            JOIN community_session s ON s.id = c.session_id
            WHERE c.organization_id = :organizationId AND s.community_id = :communityId AND c.session_id = :sessionId
            ORDER BY c.decided_at ASC, c.id ASC
            """)
    List<ActionItemCheckpointEntity> findByOrganizationIdAndCommunityIdAndSessionId(
            @Param("organizationId") String organizationId, @Param("communityId") Long communityId,
            @Param("sessionId") Long sessionId);

    /**
     * An item's carry count is not a counter column: it is {@code count(*)} of its own checkpoint log filtered to
     * {@link CheckpointOutcome#CARRIED}, so a carry decided at a session is provable from the log rather than trusted
     * to a running total that could drift from it.
     */
    long countByOrganizationIdAndActionItemIdAndOutcome(String organizationId, Long actionItemId,
            CheckpointOutcome outcome);

    /**
     * The batched form of {@link #countByOrganizationIdAndActionItemIdAndOutcome}, for a whole page of items:
     * {@link ActionItemReadModel#list} groups the rows this returns by {@code actionItemId} and counts them in Java,
     * the same shape {@code TopicVoteRepository.findByOrganizationIdAndTopicIdIn} already uses for a page of votes -
     * one query for every item on the page rather than one per row.
     */
    List<ActionItemCheckpointEntity> findByOrganizationIdAndActionItemIdInAndOutcome(String organizationId,
            Collection<Long> actionItemIds, CheckpointOutcome outcome);
}
