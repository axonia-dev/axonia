package dev.axonia.communication.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.communication.MessageOutcome;
import lombok.RequiredArgsConstructor;

/**
 * What outlives a message: the subject it was about and five integers. One thing writes it, one thing reads it back.
 *
 * <p>
 * The guarantee this table exists to carry - that a subject deleted at ninety days can never be re-enqueued - does not
 * live in a method on this class. It lives in {@link JdbcOutbox#upsertMessage}'s own
 * {@code WHERE NOT EXISTS (SELECT 1 FROM communication_expired_message ...)} clause, folded directly into the insert
 * that would otherwise re-create the subject: see that method's own javadoc for the check-then-insert race that folding
 * it in, rather than querying this class first, is what closes. This class carries the table's read and write side
 * only.
 *
 * <p>
 * {@link #outcomeFor} is why {@code events} needs no change: a sender's counts come back from here once the rows they
 * were computed from are gone, so {@code SessionMapper.releaseState} still reads {@code sent > 0} and still returns
 * {@code SENT}, with no clock involved and none of the ambiguity a clock would have introduced against a backfilled
 * session that was never produced at all.
 *
 * <p>
 * The two queries below scope themselves differently, not by one uniform rule. {@link #outcomeFor} reads
 * {@code organization_id = current_setting('app.current_org', true)} straight from the GUC, layered on top of this
 * table's RLS policy as defence in depth, for the reason {@link JdbcOutbox}'s class javadoc gives: an unscoped caller
 * matches nothing rather than throwing. {@link #record} carries no organization predicate of its own and relies on RLS
 * alone: it is called only from the retention pass, which - like every other per-organization pass in this module,
 * {@link SweepUnit#drain} and {@link SweepUnit#produce} among them - runs one organization at a time inside
 * {@code OrganizationScope.runAs}, so every id in {@code messageIds} was itself selected under that same scope before
 * ever reaching this method.
 */
@Repository
@RequiredArgsConstructor
class ExpiredMessages {

    private final NamedParameterJdbcTemplate jdbc;

    Optional<MessageOutcome> outcomeFor(String messageTypeCode, String subjectRef) {

        return jdbc.query("""
                SELECT pending_count, sent_count, suppressed_count, unreachable_count, abandoned_count
                  FROM communication_expired_message
                 WHERE organization_id = current_setting('app.current_org', true)
                   AND message_type = :type
                   AND subject_ref = :subject
                """, new MapSqlParameterSource().addValue("type", messageTypeCode).addValue("subject", subjectRef),
                (rs, rowNum) -> MessageOutcome.builder().pending(rs.getInt("pending_count"))
                        .sent(rs.getInt("sent_count")).suppressed(rs.getInt("suppressed_count"))
                        .unreachable(rs.getInt("unreachable_count")).abandoned(rs.getInt("abandoned_count")).build())
                .stream().findFirst();
    }

    /**
     * Writes one record per message, counting only {@code EMAIL} rows. That mirrors {@link JdbcOutbox#outcomeFor}'s own
     * {@code AND d.channel = 'EMAIL'} filter for the same reason: "delivered: 41" must keep meaning forty-one people,
     * and neither a community's chat post nor a member's own in-app entry is a recipient whose count a sender should
     * read. {@code pending_count} is always written as the literal {@code 0} - this method does not check, and does not
     * promise, that no delivery for the message is still non-terminal; that guarantee, if it holds, is the retention
     * pass's own to keep, by calling this method only for messages it has already confirmed are all-terminal. The
     * column is stored anyway so the mapping back to {@link MessageOutcome} stays total rather than carrying a special
     * case.
     *
     * <p>
     * {@code IS NULL OR NOT IN}, not {@code NOT IN} alone: {@code suppressed_reason} carries no {@code NOT NULL}, and
     * {@code NOT IN} against a {@code NULL} reason is {@code NULL}, so such a row would satisfy neither this
     * {@code FILTER} nor the {@code IN} one below it and silently vanish from both {@code suppressed_count} and
     * {@code unreachable_count}, while {@link JdbcOutbox#outcomeFor}'s live path still counts it as suppressed. No
     * {@code markSuppressed} call site passes a null reason today; the guard removes the divergence before it can
     * become one.
     *
     * <p>
     * The returned count is rows actually inserted, not messages considered: {@code ON CONFLICT DO NOTHING} means a
     * retry over an id whose subject a previous, partially-committed pass already recorded returns 0 for that id
     * without erroring, exactly as intended - a caller must not read 0 here as the retention pass having failed to do
     * its work.
     */
    int record(List<Long> messageIds, Instant expiredAt) {

        if (messageIds.isEmpty()) {
            return 0;
        }
        return jdbc.update("""
                INSERT INTO communication_expired_message
                    (organization_id, message_type, subject_ref, pending_count, sent_count, suppressed_count,
                     unreachable_count, abandoned_count, expired_at)
                SELECT m.organization_id, m.message_type, m.subject_ref,
                       0,
                       count(*) FILTER (WHERE d.channel = 'EMAIL' AND d.status = 'SENT'),
                       count(*) FILTER (WHERE d.channel = 'EMAIL' AND d.status = 'SUPPRESSED'
                                          AND (d.suppressed_reason IS NULL
                                               OR d.suppressed_reason NOT IN (:unreachable))),
                       count(*) FILTER (WHERE d.channel = 'EMAIL' AND d.status = 'SUPPRESSED'
                                          AND d.suppressed_reason IN (:unreachable)),
                       count(*) FILTER (WHERE d.channel = 'EMAIL' AND d.status = 'ABANDONED'),
                       :now
                  FROM communication_message m
                  LEFT JOIN communication_delivery d ON d.message_id = m.id
                 WHERE m.id IN (:ids)
                 GROUP BY m.id, m.organization_id, m.message_type, m.subject_ref
                ON CONFLICT (organization_id, message_type, subject_ref) DO NOTHING
                """, new MapSqlParameterSource().addValue("ids", messageIds).addValue("now", Timestamp.from(expiredAt))
                .addValue("unreachable", SuppressionReason.UNREACHABLE));
    }
}
