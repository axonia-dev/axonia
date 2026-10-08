package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

public interface SessionRepository
        extends
            CrudRepository<SessionEntity, Long>,
            PagingAndSortingRepository<SessionEntity, Long>,
            SessionRepositoryCustom {

    Optional<SessionEntity> findByOrganizationIdAndId(String organizationId, Long id);

    Optional<SessionEntity> findByOrganizationIdAndSeriesIdAndOccurrenceDate(String organizationId, Long seriesId,
            LocalDate occurrenceDate);

    /**
     * The batched form of {@link #findByOrganizationIdAndId}, for {@link CheckpointResolver#resolveAll}: one query for
     * every distinct pinned checkpoint session on a page of action items, rather than one per row. The community is
     * part of the predicate rather than assumed from the pin: a session id is org-visible, so a read's safety must not
     * rest on an invariant a write in another class maintains.
     */
    List<SessionEntity> findByOrganizationIdAndCommunityIdAndIdIn(String organizationId, Long communityId,
            Collection<Long> ids);

    /**
     * The next pending session in {@code seriesId} - {@code status <> 'CANCELLED'}, {@code scheduledAt >= now},
     * earliest first - other than {@code excludeSessionId} itself. {@link SessionFactsAssembler} uses this for a
     * wrap-up's "coming up next" line, always excluding the session being wrapped up: in the edge case where a session
     * is marked HELD before its own {@code scheduledAt} has passed, an un-excluded query could return that very session
     * as its own "what's next" instead of the genuine next occurrence.
     */
    @Query("""
            SELECT * FROM community_session
            WHERE organization_id = :organizationId AND series_id = :seriesId AND id <> :excludeSessionId
              AND status <> 'CANCELLED' AND scheduled_at >= :now
            ORDER BY scheduled_at ASC
            LIMIT 1
            """)
    Optional<SessionEntity> findPendingForSeriesExcluding(@Param("organizationId") String organizationId,
            @Param("seriesId") Long seriesId, @Param("excludeSessionId") Long excludeSessionId,
            @Param("now") Instant now);

    /**
     * Deliberately a different "upcoming" than {@link #findSoonestUpcoming}: this one counts {@code SKIPPED} sessions
     * and includes one scheduled exactly at {@code now} ({@code >=}), because it feeds a community's own upcoming-count
     * display, where a skipped occurrence is still a dated thing on the calendar worth counting. A checkpoint has to
     * point at a session someone can actually be answered at, so it excludes {@code HELD} and {@code SKIPPED} and
     * requires strictly {@code >} - the two queries answer different questions on purpose, not by drift.
     */
    @Query("""
            SELECT count(*) FROM community_session
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND status <> 'CANCELLED' AND scheduled_at >= :now
            """)
    long countUpcoming(@Param("organizationId") String organizationId, @Param("communityId") Long communityId,
            @Param("now") Instant now);

    @Query("""
            SELECT DISTINCT to_char(COALESCE(occurrence_date, (scheduled_at AT TIME ZONE 'UTC')::date), 'YYYY-MM') AS ym
            FROM community_session
            WHERE organization_id = :organizationId AND community_id = :communityId
            ORDER BY ym
            """)
    List<String> findActiveMonths(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId);

    /**
     * Ids of scheduled sessions due a {@code SESSION_UPCOMING} notice: their community has opted in, and the session's
     * own release intent, {@code upcomingReleaseAt}, falls in {@code (since, now]}. A session carrying no intent never
     * matches - "null means no notice" - rather than falling back to any clock-derived default, exactly as
     * {@link #findWrapUpSessionIds} treats a missing wrap-up release. A projected occurrence becomes a real
     * {@code community_session} row only on first touch, so this reaches only sessions that have already been
     * materialised - the honest rule, not a workaround. Returns ids, not entities: the producer re-fetches each one
     * through {@link #findByOrganizationIdAndId} so {@code sections} (a {@code @MappedCollection}, not populated by a
     * flat join projection like this one) loads correctly.
     *
     * <p>
     * <b>Silence is now an intent rather than an arithmetic accident.</b> This query used to derive the due instant as
     * {@code scheduledAt - session_upcoming_lead_days}, so a session materialised or rescheduled at short notice -
     * scheduled tomorrow under a three-day lead window - carried a due instant two days in the past, already below any
     * watermark more than a few minutes old, and went unannounced purely as a side effect of the formula. Such a
     * session now gets a stored null instead, decided once when the row was created and reportable to the organizer,
     * who can still choose to send a notice. The community's {@code session_upcoming_lead_days} therefore no longer
     * appears here at all: it decides the default at creation, not what is due now.
     *
     * <p>
     * {@code s.scheduled_at > :now} is kept alongside the release window, not made redundant by it: in steady state
     * {@code since} sits only a produce interval behind {@code now}, so the release window alone already implies a
     * future {@code scheduledAt}, but on a first run with no watermark row {@code since} is
     * {@link java.time.Instant#EPOCH} - deliberately, since {@code UpcomingSessionProducer} has no configured max-age
     * backstop of its own - and without this guard that unbounded {@code since} would match a session already under
     * way, or long past, just because nobody has ever produced for this organization before. Enforced by
     * {@code SessionRepositoryTest#findUpcomingNoticeSessionIds_excludesAPastSession}.
     */
    @Query("""
            SELECT s.id FROM community_session s
              JOIN communities c ON c.id = s.community_id AND c.organization_id = s.organization_id
             WHERE s.organization_id = :organizationId
               AND s.status = 'SCHEDULED'
               AND c.notify_session_upcoming
               AND s.scheduled_at > :now
               AND s.upcoming_release_at IS NOT NULL
               AND s.upcoming_release_at <= :now
               AND s.upcoming_release_at > :since
            """)
    List<Long> findUpcomingNoticeSessionIds(@Param("organizationId") String organizationId, @Param("now") Instant now,
            @Param("since") Instant since);

    /**
     * Ids of held sessions due a {@code SESSION_WRAP_UP}: their community has opted in, and the session's own due
     * instant, {@code wrapUpReleaseAt}, falls in {@code (since, now]}. A session with no release instant at all never
     * matches - a permanent suppression - rather than falling back to any clock-derived default.
     *
     * <p>
     * {@code since} is the exclusive lower bound, not a suggestion: the caller passes either a previously stored
     * watermark or, absent one, {@code now - CommunicationWindows.wrapUpMaxAge()} as the first-run backstop. The bound
     * is exclusive ({@code >}, not {@code >=}) because in steady state {@code since} is the exact {@code now} an
     * earlier tick already claimed through - a session released at precisely that instant was already produced then,
     * and an inclusive bound would produce it a second time on this tick.
     */
    @Query("""
            SELECT s.id FROM community_session s
              JOIN communities c ON c.id = s.community_id AND c.organization_id = s.organization_id
             WHERE s.organization_id = :organizationId
               AND s.status = 'HELD'
               AND c.notify_session_wrap_up
               AND s.wrap_up_release_at IS NOT NULL
               AND s.wrap_up_release_at <= :now
               AND s.wrap_up_release_at > :since
            """)
    List<Long> findWrapUpSessionIds(@Param("organizationId") String organizationId, @Param("now") Instant now,
            @Param("since") Instant since);

    /**
     * Ids of scheduled sessions whose due instant - {@code scheduledAt + offsetDays} - falls in {@code (since, now]}:
     * the shared query behind both arms of the {@code SESSION_AWAITING_CLOSE} nudge, called once with
     * {@code offsetDays = 1} for the first nudge and again with {@code offsetDays = 7} for the second, each against its
     * own watermark so the outbox's unique key lets both fire independently rather than the second colliding with the
     * first. No community opt-out reaches this query - the nudge is not a courtesy a community setting can silence.
     *
     * <p>
     * <b>Deliberately no floor against {@code created_at}.</b> A session materialised or rescheduled late enough that
     * {@code scheduledAt + offsetDays} already sits below the watermark is accepted to go unnudged rather than nudged
     * immediately on creation. The upcoming notice used to share this shape and no longer does: it carries a stored
     * intent, so its silence is an organizer's to see and to override. This nudge keeps its derived due instant on
     * purpose - it is an organizer-only reminder about their own unclosed session, and a control to suppress it is a
     * control to suppress a reminder to yourself.
     *
     * <p>
     * {@code since} is the same kind of exclusive floor {@link #findWrapUpSessionIds} takes, for the same reason: the
     * caller passes a stored watermark or, absent one, {@code now - CommunicationWindows.awaitingCloseMaxAge()}.
     */
    @Query("""
            SELECT s.id FROM community_session s
             WHERE s.organization_id = :organizationId
               AND s.status = 'SCHEDULED'
               AND s.closed_at IS NULL
               AND (s.scheduled_at + make_interval(days => :offsetDays)) <= :now
               AND (s.scheduled_at + make_interval(days => :offsetDays)) > :since
            """)
    List<Long> findOverdueUnclosedSessionIds(@Param("organizationId") String organizationId,
            @Param("offsetDays") int offsetDays, @Param("since") Instant since, @Param("now") Instant now);

    /**
     * The session an unpinned checkpoint late-binds to: the community's soonest still-upcoming session, or empty when
     * none is scheduled yet. {@code HELD} is excluded alongside {@code CANCELLED}/{@code SKIPPED} - a session already
     * held is no longer "upcoming" even if its own {@code scheduledAt} has not been touched.
     */
    @Query("""
            SELECT * FROM community_session
            WHERE organization_id = :organizationId AND community_id = :communityId
              AND status NOT IN ('CANCELLED', 'SKIPPED', 'HELD') AND scheduled_at > :now
            ORDER BY scheduled_at ASC LIMIT 1
            """)
    Optional<SessionEntity> findSoonestUpcoming(@Param("organizationId") String organizationId,
            @Param("communityId") Long communityId, @Param("now") Instant now);
}
