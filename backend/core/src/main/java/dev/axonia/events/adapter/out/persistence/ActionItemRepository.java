package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.events.application.CheckpointResolver;
import dev.axonia.events.application.SessionCommunityGuard;
import dev.axonia.events.application.SessionFactsAssembler;
import dev.axonia.events.application.SessionMapper;
import dev.axonia.events.domain.LateBindingPin;

public interface ActionItemRepository
        extends
            CrudRepository<ActionItemEntity, Long>,
            PagingAndSortingRepository<ActionItemEntity, Long>,
            ActionItemRepositoryCustom {

    Optional<ActionItemEntity> findByOrganizationIdAndId(String organizationId, Long id);

    /**
     * The batched hydration {@link SessionFactsAssembler} needs for a session's checked items: the checkpoint log names
     * only an {@code action_item_id}, never a title or an assignee, so the wrap-up's "What was checked" section joins
     * this against {@link ActionItemCheckpointRepository#findByOrganizationIdAndCommunityIdAndSessionId} by hand - one
     * query for the whole page of checkpoint rows, the same shape {@code TopicRepository#findByOrganizationIdAndIdIn}
     * and its siblings already use.
     */
    List<ActionItemEntity> findByOrganizationIdAndIdIn(String organizationId, Collection<Long> ids);

    /**
     * The items this session owes an answer on. Only {@code OPEN} ones: a done or dropped item has already been
     * answered, and a carried one went back to a null checkpoint, so a second follow-up pass lists nothing twice.
     * {@code communityId} is checked rather than derived, matching {@link #findByOriginSessionId}: a session id is
     * visible org-wide on purpose, so a {@code checkpoint_session_id} alone never proves which community that session
     * belongs to.
     */
    @Query("""
            SELECT * FROM action_item
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND checkpoint_session_id = :sessionId AND outcome = 'OPEN'
            ORDER BY due_date ASC NULLS LAST, id ASC
            """)
    List<ActionItemEntity> findOpenByCheckpointSession(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId, @Param("sessionId") Long sessionId);

    /**
     * The items raised at {@code sessionId}, for the wrap-up's "What we agreed to do" section. {@code communityId} is
     * checked rather than derived: a session id is visible org-wide on purpose, so an {@code origin_session_id} alone
     * never proves which community that session belongs to.
     */
    @Query("""
            SELECT * FROM action_item
            WHERE organization_id = :organizationId AND community_id = :communityId AND origin_session_id = :sessionId
            ORDER BY (outcome <> 'OPEN') ASC, due_date ASC NULLS LAST, id ASC
            """)
    List<ActionItemEntity> findByOriginSessionId(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId, @Param("sessionId") Long sessionId);

    /**
     * The one moment a late binding resolves: every open item of the community that was already waiting gets this
     * session as its checkpoint. Two arms reach it. An unpinned item is the ordinary one. A stale-pinned one is the
     * recovery arm: its checkpoint names a session that has itself been settled without ever answering it, so that pin
     * can no longer resolve anywhere - {@link #pinLateBound} skipped it for being pinned, {@link #clearPinsFor} never
     * fires for a session already closed, and the follow-up pass of any later session resolves through
     * {@link #findOpenByCheckpointSession} and so never sees it. Left alone the item is stranded for good, visible only
     * on the dead session's own page. Both arms bump {@code version} and stamp {@code updated_at} from the caller's
     * instant - a bulk {@code UPDATE} that left {@code version} alone would leave a concurrently-loaded entity's
     * optimistic-lock predicate matching, so the next save would write the pre-update row back.
     *
     * <p>
     * Settled is read exactly as {@link SessionMapper#isSettled} defines it, so one definition governs both: a stamped
     * {@code closed_at}, or {@code CANCELLED} without one for rows cancelled before the V26 backfill. {@code SKIPPED}
     * needs no arm - {@link #clearPinsFor} already nulled those pins at skip time, and
     * {@link SessionCommunityGuard#requireUpcomingSessionInCommunity} refuses to write a new one. Only a pin pointing
     * at a session that has come and gone is stale: one naming a session still upcoming is an organizer's deliberate
     * choice and stays put. The session being closed is excluded from the arm as well - items an organizer pinned to it
     * while it was upcoming are already where they belong, and re-pinning them to themselves would churn
     * {@code updated_at}, which is a sort key members read.
     *
     * <p>
     * {@code created_at < :scheduledAt} is the discriminator, not the session's own past-ness: {@code close} only ever
     * runs on a session already in the past, so anchoring the write to "was this session upcoming" would never pin
     * anything. The read side ({@code ActionItemRepositoryCustomImpl.CHECKPOINT_DATE_SQL}, {@link CheckpointResolver})
     * resolves a null checkpoint to the community's next still-upcoming session, so an item raised after this session
     * already happened but before an organizer got round to closing it was never waiting on it - it is waiting on
     * whatever comes next, and must stay unpinned here or the two reads would silently disagree. It gates the recovery
     * arm too, so the two arms cannot disagree with each other about what "was already waiting" means.
     *
     * <p>
     * {@code outcome = 'OPEN'} alone separates a stale pin from an answered item: a follow-up pass leaves {@code DONE}
     * and {@code DROPPED} behind, and a carried item back at a null checkpoint, so nothing that was actually answered
     * is still {@code OPEN} and still pinned.
     *
     * <p>
     * {@code organizationId}, {@code communityId} and {@code sessionId} travel as a {@link LateBindingPin} to stay
     * under PMD's {@code ExcessiveParameterList} threshold of 5. {@code scheduledAt} and {@code at} stay outside it as
     * plain named parameters: Spring Data JDBC's SpEL binding bypasses the {@code Instant} to timestamp converter, so
     * {@code :#{#v.scheduledAt}} would reach the driver as an unparseable ISO string.
     */
    @Modifying
    @Query("""
            UPDATE action_item SET checkpoint_session_id = :#{#v.sessionId}, version = version + 1, updated_at = :at
            WHERE organization_id = :#{#v.organizationId} AND community_id = :#{#v.communityId}
              AND outcome = 'OPEN' AND created_at < :scheduledAt
              AND (checkpoint_session_id IS NULL OR EXISTS (
                    SELECT 1 FROM community_session cs
                     WHERE cs.id = action_item.checkpoint_session_id
                       AND cs.organization_id = action_item.organization_id
                       AND cs.community_id = action_item.community_id
                       AND cs.id <> :#{#v.sessionId}
                       AND (cs.closed_at IS NOT NULL OR cs.status = 'CANCELLED')))
            """)
    void pinLateBound(@Param("v") LateBindingPin v, @Param("scheduledAt") Instant scheduledAt, @Param("at") Instant at);

    /**
     * A session that will never answer anything - cancelled, or an occurrence skipped in advance - clears every item
     * that named it, so each late-binds again to whatever session comes next. Never reversed by an unskip: the item may
     * already have late-bound to a session moved in between, and re-pinning it here would silently override that.
     *
     * <p>
     * {@code communityId} is checked rather than derived, matching its two siblings: a session id is visible org-wide,
     * so a {@code checkpoint_session_id} alone never proves which community that pin belongs to.
     */
    @Modifying
    @Query("""
            UPDATE action_item SET checkpoint_session_id = NULL, version = version + 1, updated_at = :at
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND checkpoint_session_id = :sessionId AND outcome = 'OPEN'
            """)
    void clearPinsFor(@Param("organizationId") String organizationId, @Param("communityId") Long communityId,
            @Param("sessionId") Long sessionId, @Param("at") Instant at);
}
