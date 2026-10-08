package dev.axonia.communication.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * The ninety-day pass over the outbox. A message expires all at once or not at all: its record is written, then every
 * one of its deliveries and the message itself are deleted, inside one transaction.
 *
 * <p>
 * The all-or-nothing guard is the substance of it. Deleting a message's rows piecemeal would leave it reporting counts
 * for only the survivors, and a wrap-up reading "delivered: 6" because thirty-five of its rows aged out first is a
 * delivery failure as far as anybody looking at it can tell. A message with one row still pending, sending or retrying
 * is therefore left entirely alone until a later pass.
 *
 * <p>
 * {@link #expire} takes {@code now} and {@code cutoff} as two distinct instants rather than one, even though
 * {@link SweepUnit#retain} derives both from a single {@code Clock} reading. They mean different things: {@code cutoff}
 * is what {@link #candidates} selects a message's age against, and {@code now} is what {@link ExpiredMessages#record}
 * stamps the surviving record with - the moment the record was written, not the moment ninety days ago that made the
 * message eligible. Collapsing the two into one parameter, as an earlier draft of this class did, would have stamped
 * every record as having expired ninety days in the past, which is false: a record written today expired today.
 *
 * <p>
 * One transaction per organization rather than per message, unlike {@link OutboxRowProcessor}. Nothing here sends
 * anything or touches a transport, so a rollback costs only that this pass expired nothing; there is no half-written
 * external effect for a smaller boundary to protect. The batch size is what bounds the pass instead.
 *
 * <p>
 * The delete is deliberately the same shape as {@link CommunicationMemberDataEraser}'s: both carry an explicit
 * {@code organization_id = current_setting('app.current_org', true)} predicate on every statement, layered over this
 * table's RLS policy as defence in depth, even though {@link #candidates} already selected {@code messageIds} under
 * that same scope. "A message goes when its last delivery goes" stays the single rule governing a message's lifetime,
 * with retention as a second trigger for that rule rather than a second rule competing with it.
 *
 * <p>
 * {@link ExpiredMessages}'s own javadoc documents a residual race this class does not close: a producer's
 * {@code enqueue} can slip a fresh row for a subject past the expiry guard if its statement starts while this method's
 * {@code DELETE FROM communication_message} is still uncommitted. Committing {@link ExpiredMessages#record} in its own
 * transaction ahead of the deletes would close that window, at the cost of a record that could survive a later rollback
 * of the deletes it was meant to summarize - re-opening the all-or-nothing guarantee this class exists to keep. A
 * record and the deletes it summarizes must stand or fall together, so they share one transaction.
 */
@Component
@RequiredArgsConstructor
class OutboxRetention {

    private final NamedParameterJdbcTemplate jdbc;
    private final ExpiredMessages expiredMessages;

    @Transactional
    int expire(Instant now, Instant cutoff, int batchSize) {

        var messageIds = candidates(cutoff, batchSize);
        if (messageIds.isEmpty()) {
            return 0;
        }
        expiredMessages.record(messageIds, now);

        var ids = new MapSqlParameterSource().addValue("ids", messageIds);
        jdbc.update("""
                DELETE FROM communication_delivery
                 WHERE message_id IN (:ids)
                   AND organization_id = current_setting('app.current_org', true)
                """, ids);
        jdbc.update("""
                DELETE FROM communication_message
                 WHERE id IN (:ids)
                   AND organization_id = current_setting('app.current_org', true)
                """, ids);
        return messageIds.size();
    }

    /**
     * The cutoff is bound as a plain instant computed from the caller's {@code Clock}; no statement in this class calls
     * SQL {@code now()}, so nothing here is stamped by the database.
     *
     * <p>
     * The {@code NOT EXISTS} guard carries the same {@code organization_id = current_setting('app.current_org', true)}
     * predicate as {@link #expire}'s two {@code DELETE}s, for symmetry rather than necessity under RLS: with the policy
     * enforced, {@code d.message_id = m.id} alone already can't cross a tenant boundary, since {@code m} itself is
     * already scoped. Without RLS - every {@code @DataJdbcTest} against {@code jdbc:tc}, whose driver connects as the
     * container superuser - the two could disagree: an unscoped guard could count a same-{@code message_id} row from a
     * different organization's data as non-terminal (or, if none exists, simply see the same rows the scoped deletes
     * would), while the scoped deletes below only ever touch this organization's own rows. A guard that ever counted
     * more broadly than the deletes remove would let a delivery survive the message it belonged to.
     */
    private List<Long> candidates(Instant cutoff, int batchSize) {

        return jdbc.queryForList("""
                SELECT m.id
                  FROM communication_message m
                 WHERE m.organization_id = current_setting('app.current_org', true)
                   AND m.created_at < :cutoff
                   AND NOT EXISTS (SELECT 1
                                     FROM communication_delivery d
                                    WHERE d.message_id = m.id
                                      AND d.organization_id = current_setting('app.current_org', true)
                                      AND d.status NOT IN ('SENT', 'SUPPRESSED', 'ABANDONED'))
                 ORDER BY m.id
                 LIMIT :batch
                """,
                new MapSqlParameterSource().addValue("cutoff", Timestamp.from(cutoff)).addValue("batch", batchSize),
                Long.class);
    }
}
