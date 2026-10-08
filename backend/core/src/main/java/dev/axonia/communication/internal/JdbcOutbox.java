package dev.axonia.communication.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.MessageOutcome;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OrganizationContext;
import dev.axonia.communication.OutboundMessage;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The outbox that makes the idempotency guarantee structural rather than conventional. A message and its recipients are
 * two tables: {@code enqueue} writes one {@code communication_message} row carrying the template and the facts, then
 * fans out one {@code communication_delivery} row per recipient per member-addressed channel, all inside the sender's
 * transaction. Two constraints carry the guarantee between them - {@code communication_message_once} on
 * {@code (organization_id, message_type, subject_ref)} and {@code communication_delivery_once} on
 * {@code (message_id, user_id, channel)} - so a producer that runs every tick can rely on the insert conflicting rather
 * than tracking what it already sent. The {@code channel} in the delivery key is what lets one recipient's mail row and
 * their in-app row for the SAME message coexist rather than conflict with each other - see
 * {@link #insertRecipientRows}. The {@code organization_id} in the message key is what makes a {@code subject_ref} that
 * is not globally unique, such as a period key, still address each tenant separately: without it, one person in two
 * organizations loses their second message to {@code ON CONFLICT DO NOTHING} with no error to find later. Delivery rows
 * carry {@code user_id}, never an address - the address is resolved from identity at send time.
 *
 * <p>
 * {@code claim} is the other half. An earlier version of this comment called it "deliberately cross-tenant ... drains
 * every organization's due work in one pass" - that was never true of the finished design and is corrected here rather
 * than left to mislead whoever wires up the sweep next. {@link OutboxDrain#drain} is called once per organization,
 * inside {@code OrganizationScope}'s {@code app.current_org} scope. The SQL below carries an explicit
 * {@code organization_id = current_setting('app.current_org', true)} predicate - the identical expression the table's
 * own {@code organization_isolation} row-level-security policy uses - layered on top of RLS as defence in depth, the
 * same convention {@code markSent}/{@code markSuppressed}/{@code markFailed}/{@code release} below and
 * {@link NotificationPreferenceRepository} already follow. Reading straight from the GUC, rather than from
 * {@link OrganizationContext#organizationId()}, is what keeps that defence compatible with an unscoped caller:
 * {@code current_setting(..., true)} returns {@code NULL} when the GUC was never set, {@code organization_id = NULL} is
 * never true in SQL, so the predicate matches nothing and the {@code UPDATE} claims zero rows - silently, not by
 * throwing, exactly as row-level security treats an unscoped connection. {@code CommunicationRlsIsolationTest} proves
 * both halves directly against the real {@code axonia_app} role: an unscoped connection sees nothing, and a scoped one
 * sees only its own organization's rows. {@link #outcomeFor}, the {@link MessageOutcomePort} half of this class, is a
 * {@code SELECT} rather than a write, but follows the identical predicate for the identical reason: an unscoped read
 * must fail closed to all zeros, not leak another organization's counts.
 *
 * <p>
 * The predicate earns its keep beyond the primary, RLS-scoped datasource, too: the shared {@code jdbc:tc}
 * Testcontainers connection every other test in this suite uses connects as the container superuser, which bypasses RLS
 * entirely by table ownership. Without this predicate, a claimable row any test left behind on that shared connection
 * would be visible - and claimable - to any other organization's {@link OutboxDrain#drain} running against it; with it,
 * the claim fails closed by construction even when RLS itself is bypassed, so a test leaving a stray row behind is a
 * hygiene problem rather than a cross-tenant data leak in the suite. {@code FOR UPDATE SKIP LOCKED} means a second
 * backend instance claiming concurrently skips whatever the first already has locked instead of blocking on it, whether
 * that is a redeploy's briefly-overlapping old and new instance or a second organization's own drain running at the
 * same moment.
 *
 * <p>
 * {@link #claim} and {@link #markFailed} both carry their own {@code @Transactional}, plain {@code REQUIRED} rather
 * than {@code REQUIRES_NEW}: {@link OutboxDrain} itself holds no transaction of its own any more, and neither does
 * anything else in the real call chain above it ({@code CommunicationSweep.drainTick}, {@code SweepUnit.drain}), so in
 * production {@code REQUIRED} always finds no ambient transaction to join and opens a fresh one of its own - identical,
 * in practice, to {@code REQUIRES_NEW}. {@code REQUIRED} is what lets this same method still run correctly inside
 * {@code CommunicationJdbcTestSupport}'s {@code @DataJdbcTest} slice, whose {@code TransactionalTestExecutionListener}
 * wraps an entire test method in ONE ambient, rolled-back-at-the-end transaction: {@code REQUIRES_NEW} would suspend
 * that transaction and open a genuinely separate connection, which - under read-committed isolation - cannot see a row
 * {@link #enqueue} wrote moments earlier on the STILL-UNCOMMITTED test transaction, breaking every test in that suite
 * that enqueues, then claims, in the same method. That is what makes the duplicate-send window genuinely one message
 * rather than one batch. The claim used to be step one of a single transaction spanning the whole tick - every
 * producer's inserts, the claim itself, up to {@code batchSize} SMTP sends, and every mark - so a kill anywhere in that
 * tick rolled back the claim along with every mark already written, and the next tick reclaimed and resent every row in
 * the batch, including whichever ones had already been durably delivered. With the claim and each row's processing (see
 * {@link OutboxRowProcessor}) each in their own transaction, a kill can only ever undo the one row that was actually in
 * flight when it happened - see {@link OutboxDrain}'s own class javadoc for the row-by-row walkthrough.
 */
@Repository
@RequiredArgsConstructor
class JdbcOutbox implements CommunicationPort, MessageOutcomePort {

    static final RowMapper<OutboxEntity> ROW_MAPPER = JdbcOutbox::mapRow;

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final ChatDestinationsPort chatDestinationsPort;
    private final ExpiredMessages expiredMessages;

    /**
     * A community-wide notice - {@code SESSION_UPCOMING}, {@code SESSION_WRAP_UP} - can address every member of a guild
     * several hundred strong; one {@code jdbc.update} per recipient would mean several hundred round trips inside this
     * method's own transaction for a single session, every tick. {@code batchUpdate} sends all of them in one round
     * trip instead - once for the mail row, once more for the in-app row, since {@link #insertRecipientRows} is called
     * once per member-addressed channel. Each element of a batch's returned {@code int[]} is that recipient's own row
     * in that channel - 1 for an actual insert, 0 where {@code ON CONFLICT DO NOTHING} found a duplicate - and the
     * returned count is the number of positive elements, not their sum; see the comment on that line for why the two
     * differ.
     *
     * <p>
     * Nothing is written at all - not even the message row - when {@code recipients} is empty AND {@code message}'s
     * type is not one {@link ChatDestinationsPort#channelTypes()} names: that is the true no-op
     * {@code JdbcOutboxTest#enqueueingWithNoRecipientsIsANoOpThatNeverTouchesTheDatabase} pins, and it still must hold,
     * because {@link #upsertMessage} runs unconditionally as soon as either half of that OR is true and a stray call
     * from an empty audience must not leave an orphan message behind (see that method's own javadoc). A community-wide
     * message with no members left to mail is not the no-op case, though: it still has a channel worth posting to, so
     * {@code chatEligible} alone is enough to proceed - see {@link #insertChatRow}.
     */
    @Override
    @Transactional
    public int enqueue(OutboundMessage message) {

        var recipients = message.recipientUserIds();
        var chatEligible = chatDestinationsPort.channelTypes().contains(message.type());
        if (recipients.isEmpty() && !chatEligible) {
            return 0;
        }

        var organizationId = organizationContext.organizationId();
        var now = Timestamp.from(clock.instant());
        var upserted = upsertMessage(message, organizationId, now);

        // An expired subject is never enqueued again - see upsertMessage's own javadoc for how its WHERE NOT EXISTS
        // clause folds that guard into the insert itself, atomically, rather than a preceding check this method would
        // otherwise need. An empty result here means exactly that: nothing was written, so nothing else in this call
        // may write either.
        if (upserted.isEmpty()) {
            return 0;
        }

        var messageId = upserted.get();
        var chatRows = chatEligible ? insertChatRow(organizationId, messageId, now) : 0;

        if (recipients.isEmpty()) {
            return chatRows;
        }

        var batchArgs = recipients.stream()
                .map(userId -> (SqlParameterSource) new MapSqlParameterSource().addValue("org", organizationId)
                        .addValue("message", messageId).addValue("user", userId)
                        .addValue("status", OutboxStatus.PENDING.name()).addValue("now", now))
                .toArray(SqlParameterSource[]::new);

        var mailRows = insertRecipientRows(batchArgs, DeliveryChannel.EMAIL);
        var inAppRows = insertRecipientRows(batchArgs, DeliveryChannel.IN_APP);

        return chatRows + mailRows + inAppRows;
    }

    /**
     * The same recipient list, written once per member-addressed channel. The two batches share their parameter
     * sources: a recipient's mail row and their in-app row differ in nothing but the channel, and
     * {@code communication_delivery_once} now carries that channel, which is what lets both exist for one message.
     *
     * <p>
     * Counted, not summed, for the identical reason and under the identical accepted tradeoff as this class's other
     * batch inserts - see {@link #expandChatRow}'s closing comment.
     */
    private int insertRecipientRows(SqlParameterSource[] batchArgs, DeliveryChannel channel) {

        var inserted = jdbc.batchUpdate(INSERT_RECIPIENT_ROW_SQL.get(channel), batchArgs);

        return (int) Arrays.stream(inserted).filter(count -> count > 0).count();
    }

    /**
     * One statement per member-addressed channel, looked up by {@link #insertRecipientRows} rather than assembled with
     * {@code channel.name()} at call time. Formatting a fresh statement on every enqueue read its text block's own
     * newlines as a SpotBugs {@code VA_FORMAT_STRING_USES_NEWLINE} finding - switching the text block to {@code %n}
     * would have fixed that by changing the SQL text itself for no reason, so the statement is built once per channel
     * instead, the same way {@link #CLAIM_SQL} is built once for {@code claim}.
     *
     * <p>
     * {@code channel} is baked into each statement's text rather than bound as a parameter, exactly like
     * {@link #CLAIM_SQL}'s own {@code status = '...'} literals are not bound - see that constant's own javadoc. Here
     * the reason is simpler than the planner argument that javadoc makes: {@link DeliveryChannel} is an enum this class
     * controls, {@code EMAIL} and {@code IN_APP} are the only two member-addressed values it declares, and neither ever
     * arrives from outside this class, so there is nothing a bound parameter would protect against that a compile-time
     * literal does not already close off.
     */
    private static final Map<DeliveryChannel, String> INSERT_RECIPIENT_ROW_SQL = Map.of(DeliveryChannel.EMAIL, """
            INSERT INTO communication_delivery
                (organization_id, message_id, user_id, channel, status, created_at, updated_at, version)
            VALUES (:org, :message, :user, 'EMAIL', :status, :now, :now, 0)
            ON CONFLICT (message_id, user_id, channel) DO NOTHING
            """, DeliveryChannel.IN_APP, """
            INSERT INTO communication_delivery
                (organization_id, message_id, user_id, channel, status, created_at, updated_at, version)
            VALUES (:org, :message, :user, 'IN_APP', :status, :now, :now, 0)
            ON CONFLICT (message_id, user_id, channel) DO NOTHING
            """);

    /**
     * Written once {@link #enqueue} has already decided the type is chat-eligible, never gated on whether this
     * community has a destination configured: the whole spine resolves governance and destination at send time, up to
     * 48 hours of lead time or grace passes in between, and a leader who configures a destination inside that window
     * must have their next message post. A community with no destination therefore gets one row the gate suppresses as
     * NO_DESTINATION, which keeps "why did nothing post" answerable from the record rather than inferred from an
     * absence.
     *
     * <p>
     * {@code user_id} is {@code NULL} - a chat row is addressed to a room, not a person - so the per-recipient
     * {@code communication_delivery_once} unique constraint does not apply to it. Two partial unique indexes replaced
     * the single {@code communication_delivery_chat_once} this method used to rely on:
     * {@code communication_delivery_chat_placeholder_once} on {@code (message_id) WHERE channel = 'CHAT' AND
     * chat_destination_id IS NULL} is the {@code ON CONFLICT} arbiter below, so a re-run of the same message finds its
     * placeholder already there and does nothing, exactly like the per-recipient rows around it;
     * {@code communication_delivery_chat_post_once} on {@code (message_id, chat_destination_id) WHERE channel = 'CHAT'
     * AND chat_destination_id IS NOT NULL} is {@link #expandChatRow}'s own arbiter, once this placeholder has resolved
     * into one row per destination.
     */
    private int insertChatRow(String organizationId, Long messageId, Timestamp now) {

        return jdbc.update("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, channel, status, created_at, updated_at, version)
                VALUES (:org, :message, NULL, 'CHAT', :status, :now, :now, 0)
                ON CONFLICT (message_id) WHERE channel = 'CHAT' AND chat_destination_id IS NULL DO NOTHING
                """, new MapSqlParameterSource().addValue("org", organizationId).addValue("message", messageId)
                .addValue("status", OutboxStatus.PENDING.name()).addValue("now", now));
    }

    /**
     * Writes one real chat delivery per destination, beside the placeholder that produced them. Idempotent by
     * construction: {@code communication_delivery_chat_post_once} is the {@code ON CONFLICT} arbiter, which is what
     * lets this method re-run harmlessly rather than duplicate rows. That re-run has two real callers, not a torn write
     * between these inserts and the placeholder's own mark - {@link OutboxRowProcessor#expand} wraps both in one
     * transaction, so nothing here is ever durably written without that mark following it. The two real callers are a
     * placeholder reclaimed after an earlier attempt's transaction rolled back entirely (a database error, a killed
     * process), and two drain instances that both read the same still-unexpanded placeholder and race to expand it.
     *
     * <p>
     * Each row starts {@code PENDING} at zero attempts. That is what makes a destination's retry genuinely its own: one
     * revoked webhook exhausts its own attempts without touching the row beside it.
     */
    @Transactional
    int expandChatRow(OutboxEntity row, List<Long> destinationIds, Instant now) {

        if (destinationIds.isEmpty()) {
            return 0;
        }
        var timestamp = Timestamp.from(now);
        var batchArgs = destinationIds.stream()
                .map(destinationId -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("org", row.getOrganizationId()).addValue("message", row.getMessageId())
                        .addValue("destination", destinationId).addValue("status", OutboxStatus.PENDING.name())
                        .addValue("now", timestamp))
                .toArray(SqlParameterSource[]::new);

        var inserted = jdbc.batchUpdate("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, channel, chat_destination_id,
                     status, created_at, updated_at, version)
                VALUES (:org, :message, NULL, 'CHAT', :destination, :status, :now, :now, 0)
                ON CONFLICT (message_id, chat_destination_id)
                    WHERE channel = 'CHAT' AND chat_destination_id IS NOT NULL DO NOTHING
                """, batchArgs);

        // Counted, not summed, for the identical reason and under the identical accepted tradeoff as enqueue's own
        // batch insert above - see the comment on that method's return line. Proven here by
        // JdbcOutboxChatRowTest#expandingTwiceLeavesExactlyOneRowPerDestination, this method's own version of that
        // proof: a batch that is entirely conflicts, correctly counted as 0.
        return (int) Arrays.stream(inserted).filter(count -> count > 0).count();
    }

    /**
     * The message row is the idempotency anchor, so its facts are written once and never overwritten: re-producing the
     * same subject with freshly assembled facts leaves the frozen ones alone, which is what makes divergent facts for
     * one logical message structurally impossible rather than merely unlikely.
     *
     * <p>
     * The {@code WHERE NOT EXISTS (SELECT 1 FROM communication_expired_message ...)} clause is the guard against
     * re-enqueueing an expired subject, folded into this single statement rather than living as a preceding
     * {@code SELECT} in {@link #enqueue}. A separate check-then-insert is not atomic: a retention pass can commit
     * {@code ExpiredMessages#record} and delete this subject's message row in the window between a preceding check's
     * read and a following insert's write - a window as wide as the network round trip between two separate statements
     * - and the very next enqueue for that subject then finds no conflict and mails a stale re-release. Folding the
     * guard into this statement's own source {@code SELECT} closes that specific, dominant interleaving: there are no
     * longer two statements for a commit to land between, only one snapshot for it to land before or after. Landing
     * before it, the guard sees it and the source {@code SELECT} yields no row - nothing is inserted, nothing
     * conflicts, {@code RETURNING} yields nothing back, {@link #enqueue} sees an empty {@link Optional} and returns 0,
     * the same answer it already gives a plain duplicate.
     *
     * <p>
     * That is a real narrowing of the race, not a closing of it. A residual window remains, exactly as wide as the time
     * a retention transaction's own {@code DELETE} of this row stays uncommitted: retention writes its expiry record
     * and deletes the message row, both still uncommitted, while this statement's own snapshot is already taken and
     * does not see either write, so the guard passes and the source {@code SELECT} yields a row; the {@code INSERT}
     * then reaches the still-live conflicting tuple retention's {@code DELETE} has locked and blocks on it, exactly as
     * the conflict arm below already blocks on any concurrent writer to this key; retention commits; Postgres re-checks
     * the arbiter, finds the tuple now dead, and retries the insertion instead of running {@code DO UPDATE} against a
     * row that no longer exists - so a fresh {@code communication_message} row is written for a subject that, by the
     * time this statement finishes, is already expired. Closing that window is not this statement's call to make: it
     * would require retention to guarantee its expiry record is visible to every concurrent producer strictly before it
     * deletes the message row the record covers - committing the record in a transaction of its own, ahead of the
     * delete, for instance - and that ordering has its own cost, not a free one: a producer racing that same window
     * would then find a still-live message row, take the {@code DO UPDATE} arm against it, and have its just-written
     * deliveries removed by the delete that follows, so nothing would be mailed for that enqueue either. That tradeoff
     * belongs to retention's own transaction shape and is left open here, not decided by this statement.
     *
     * <p>
     * The conflict arm writes {@code updated_at} back to the value it already holds. That is deliberately a no-op on
     * the row and deliberately not {@code DO NOTHING}: what it buys is that the statement returns an id whenever a row
     * is written or already exists, unexpired. {@code DO NOTHING} returns no row on a conflict, and an {@code INSERT}
     * does not wait on a conflicting tuple another transaction has written but not yet committed - it simply finds
     * nothing - so two concurrent enqueues of the same {@code (organization, type, subject)} could leave the loser with
     * neither an inserted row nor a readable one. A read-back on that path would have found nothing either, and would
     * have reported a genuine race as a broken invariant. {@code DO UPDATE} blocks on the uncommitted tuple instead and
     * then returns whichever row won, which is what the caller needs and the only thing it needs.
     *
     * <p>
     * Nothing here stamps a timestamp: {@code updated_at} is set from the {@code Clock}-derived {@code now} the caller
     * passed on the insert arm, and to its own existing value on the conflict arm.
     */
    private Optional<Long> upsertMessage(OutboundMessage message, String organizationId, Timestamp now) {

        var params = new MapSqlParameterSource().addValue("org", organizationId).addValue("type", message.type().code())
                .addValue("subject", message.subjectRef()).addValue("template", message.template())
                .addValue("facts", jsonMapper.writeValueAsString(message.facts())).addValue("now", now);

        return jdbc.query("""
                INSERT INTO communication_message
                    (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                SELECT :org, :type, :subject, :template, CAST(:facts AS jsonb), :now, :now, 0
                 WHERE NOT EXISTS (SELECT 1 FROM communication_expired_message
                                     WHERE organization_id = :org
                                       AND message_type = :type
                                       AND subject_ref = :subject)
                ON CONFLICT (organization_id, message_type, subject_ref)
                    DO UPDATE SET updated_at = communication_message.updated_at
                RETURNING id
                """, params, (rs, rowNum) -> rs.getLong("id")).stream().findFirst();
    }

    /**
     * Counts this subject's outbox rows by status and, for {@code SUPPRESSED} rows, by {@link SuppressionReason} - see
     * {@link MessageOutcome} for how the six {@link OutboxStatus} values, crossed with that reason, coarsen into the
     * five counts returned. The {@code GROUP BY} carries both columns rather than status alone specifically so
     * {@code SUPPRESSED}'s single status can still be split into {@code suppressed} (governance: opted out, community
     * or organization switched off, no longer a member, an unrecognized message type) and {@code unreachable}
     * ({@link SuppressionReason#UNREACHABLE}: no usable address, or no mail server to send through) without a second
     * query - see {@link MessageOutcome}'s javadoc for why that split exists at all. A suppressed row with no reason
     * counts as suppressed, never as unreachable. Carries the identical {@code app.current_org} predicate on
     * {@code organization_id} as this class's other two reads, {@link #claim} and {@link #findMessageForSubject}, for
     * the identical reason: layered on top of this table's RLS policy as defence in depth, and read from the GUC rather
     * than {@link OrganizationContext} so an unscoped caller's predicate matches nothing and every count comes back
     * zero instead of the call throwing. This class's writes - {@link #upsertMessage}, {@link #insertChatRow},
     * {@link #insertRecipientRows}, {@link #expandChatRow}, {@link #markStatus} and {@link #markFailed},
     * {@link #release} - bind an explicit {@code :org} parameter instead: each already carries (or is handed) the
     * organization id it is writing, so there is nothing to read from the GUC that the caller has not already supplied.
     * A {@code GROUP BY} with no matching rows - an unknown {@code subjectRef}, or a scope that sees nothing - returns
     * no rows at all, which {@link #countOf} already turns into zero for every status it is asked about, and the
     * {@code unreachable} sum below is zero over an empty stream for the same reason; no separate empty-result branch
     * is needed for that case to fall out correctly.
     *
     * <p>
     * {@code AND d.channel = 'EMAIL'} keeps a chat row out of every count here: "delivered: 41" must mean 41 people,
     * and a community's own chat post is a recipient to nobody. Without the filter it would count itself into
     * {@code pending} the moment {@link #enqueue} wrote it, inflating a sender's own recipient counts by exactly one
     * for every chat-eligible message.
     */
    @Override
    public MessageOutcome outcomeFor(MessageType type, String subjectRef) {

        var rows = jdbc.query("""
                SELECT d.status, d.suppressed_reason, count(*) AS total
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE m.message_type = :type
                   AND m.subject_ref = :subject
                   AND d.organization_id = current_setting('app.current_org', true)
                   AND d.channel = 'EMAIL'
                 GROUP BY d.status, d.suppressed_reason
                """, new MapSqlParameterSource().addValue("type", type.code()).addValue("subject", subjectRef),
                (rs, rowNum) -> new StatusReasonCount(rs.getString("status"), rs.getString("suppressed_reason"),
                        rs.getLong("total")));

        if (rows.isEmpty()) {
            return expiredMessages.outcomeFor(type.code(), subjectRef)
                    .orElseGet(() -> MessageOutcome.builder().build());
        }

        var byStatus = rows.stream().collect(
                Collectors.groupingBy(StatusReasonCount::status, Collectors.summingLong(StatusReasonCount::total)));
        var unreachable = rows.stream()
                .filter(row -> OutboxStatus.SUPPRESSED.name().equals(row.status()) && row.reason() != null
                        && SuppressionReason.UNREACHABLE.contains(row.reason()))
                .mapToLong(StatusReasonCount::total).sum();

        return MessageOutcome.builder()
                .pending(countOf(byStatus, OutboxStatus.PENDING, OutboxStatus.SENDING, OutboxStatus.FAILED))
                .sent(countOf(byStatus, OutboxStatus.SENT))
                .suppressed(countOf(byStatus, OutboxStatus.SUPPRESSED) - Math.toIntExact(unreachable))
                .unreachable(Math.toIntExact(unreachable)).abandoned(countOf(byStatus, OutboxStatus.ABANDONED)).build();
    }

    /**
     * Row shape for {@link #outcomeFor}'s {@code GROUP BY status, suppressed_reason} - {@code reason} is null for every
     * non-{@code SUPPRESSED} status.
     */
    private record StatusReasonCount(String status, String reason, long total) {
    }

    /**
     * The one message row for a subject, if a sender has already enqueued it. Its {@code facts} are the ones a real
     * send will use or already used, which is what {@code MessageRenderAdapter} previews from rather than re-deriving
     * live facts that could drift. Carries the same {@code current_setting('app.current_org', true)} predicate as
     * {@link #outcomeFor} and {@link #claim} - this class's other two reads, see {@link #outcomeFor}'s own javadoc for
     * why the class's reads and writes scope themselves differently - so an unscoped read fails closed to empty rather
     * than leaking another organization's message.
     */
    Optional<MessageEntity> findMessageForSubject(MessageType type, String subjectRef) {

        return jdbc
                .query("""
                        SELECT id, organization_id, message_type, subject_ref, template, facts
                          FROM communication_message
                         WHERE message_type = :type
                           AND subject_ref = :subject
                           AND organization_id = current_setting('app.current_org', true)
                         LIMIT 1
                        """, new MapSqlParameterSource().addValue("type", type.code()).addValue("subject", subjectRef),
                        (rs, rowNum) -> MessageEntity.builder().id(rs.getLong("id"))
                                .organizationId(rs.getString("organization_id"))
                                .messageType(rs.getString("message_type")).subjectRef(rs.getString("subject_ref"))
                                .template(rs.getString("template")).facts(rs.getString("facts")).build())
                .stream().findFirst();
    }

    private static int countOf(Map<String, Long> byStatus, OutboxStatus... statuses) {

        return Arrays.stream(statuses).mapToInt(status -> Math.toIntExact(byStatus.getOrDefault(status.name(), 0L)))
                .sum();
    }

    /**
     * Claims up to {@code query.batchSize()} rows for sending, oldest first, selecting three kinds and returning two:
     *
     * <ul>
     * <li>{@code PENDING} - never sent. Returned, claimed into {@code SENDING}.
     * <li>{@code FAILED}, {@code attempts} still under {@code query.maxAttempts()}, and {@code updated_at} older than
     * {@code query.retryBackoff()} - a retry after a backoff, not implementable against a {@code PENDING}-only claim.
     * Returned, claimed into {@code SENDING}.
     * <li>{@code SENDING}, {@code claimed_at} older than {@code query.claimLease()} - the reclaim. A row left here is
     * one whose sweep died between claiming it and marking its outcome, most routinely a redeploy's SIGTERM
     * interrupting the running tick and Spring's scheduler with it, not a rare crash. Left unclaimed, such a row is
     * stranded forever: never sent, never suppressed, invisible. Split in two by {@code attempts}: still under
     * {@code query.maxAttempts()}, it is reclaimed into {@code SENDING} with {@code attempts} bumped, exactly like a
     * {@code FAILED} retry - <b>not returned</b> to the caller.
     * </ul>
     *
     * <p>
     * A {@code SENDING} row already at {@code query.maxAttempts()} is not reclaimed a third way: it becomes
     * {@code ABANDONED}, a terminal status excluded from every future {@code claim} (it is outside
     * {@code communication_delivery_claimable}'s {@code status IN (...)} entirely). Capping the reclaim this way does
     * not reintroduce the stranded-{@code SENDING} bug this widening exists to fix - what made that row a bug was being
     * invisible, never sent, never suppressed, no record it was attempted, and {@code ABANDONED} is none of those:
     * queryable, countable, alertable. Left uncapped, a row that deterministically dies right after being claimed but
     * before any mark is written would be reclaimed and re-sent every {@code claimLease} period forever, each one a
     * real duplicate delivery to a real member - worse than a row an operator can see and act on. {@code markFailed}
     * enforces the identical cap on the other path to {@code FAILED}; see its own javadoc.
     *
     * <p>
     * <b>The reclaim's accepted tradeoff.</b> The SMTP send and the {@link #markSent} write cannot commit together: a
     * kill between the send and the commit leaves a row that was in fact sent but still reads {@code SENDING}, and
     * reclaiming it sends a duplicate. The unique key on {@code (message_id, user_id)} prevents a duplicate
     * <em>row</em>, not a duplicate <em>send of the same row</em> - SMTP has no idempotency key of its own, so this
     * window cannot be closed by anything short of never reclaiming, which is exactly the stranded-forever bug this
     * widening exists to fix. We accept a rare duplicate send over silent, permanent loss.
     *
     * <p>
     * That window is now genuinely a kill, not routine operation. {@link OutboxRowProcessor#process} writes
     * {@code markSent} BEFORE handing the mail to {@code MailBatch} - see its javadoc for why - so the failing
     * <em>mark</em> can no longer duplicate a mail that already left, and the {@code UPDATE}'s exclusive row lock is
     * held for the whole send, which the {@code FOR UPDATE SKIP LOCKED} below honours: a second instance whose tick
     * arrives after this row's {@code claimLease} expired skips a row still genuinely in flight rather than re-sending
     * it. {@code claimLease} should still comfortably outlast {@code batchSize} rows' worst-case send time - see
     * {@code CommunicationProperties}' javadoc - because a lease that expires mid-batch still churns; it is no longer
     * the difference between a duplicate and no duplicate for a concurrently claimed row.
     *
     * <p>
     * The inner {@code SELECT}'s three {@code status = '...'} comparisons are literals, not bound parameters, for the
     * reason the single {@code status = 'PENDING'} comparison was before this method grew two more branches:
     * {@code communication_delivery_claimable} is a partial index on exactly
     * {@code status IN ('PENDING', 'FAILED', 'SENDING')}, and the planner can only prove a partial index matches a
     * query's predicate when it can see the literal values at plan time - a bound {@code :status} parameter defeats
     * that once pgjdbc switches to a server-side prepared statement (its default after the fifth execution of the same
     * SQL text), which a sweep running on a timer will do almost immediately. The index prunes to the three claimable
     * statuses only; it cannot also prove {@code attempts}/{@code updated_at}/{@code claimed_at} membership at plan
     * time, so a {@code FAILED} row still inside its backoff window or a {@code SENDING} row still inside its lease is
     * scanned and filtered rather than excluded outright by the index - a real cost under a large backlog of either,
     * worth knowing about rather than assuming away. {@code ABANDONED} rows never enter this index at all, which is the
     * other half of why they are terminal rather than merely excluded from the {@code SENDING} branch's retry.
     *
     * <p>
     * The outer {@code SELECT}'s {@code ORDER BY c.id} makes the RETURNED list actually match the "oldest first" this
     * javadoc's opening line already promised - the inner {@code SELECT ... ORDER BY id ... LIMIT} only ever ordered
     * which rows get selected for the {@code UPDATE}, not the order {@code RETURNING} then hands them back in, which
     * Postgres leaves unspecified for a {@code WITH ... UPDATE ... RETURNING} CTE. One claimable row per recipient
     * never surfaced that gap; a recipient's mail row and their in-app row can now both be PENDING at once (see
     * {@link #insertRecipientRows}), and a caller such as {@code JdbcOutboxTest} that reads
     * {@code claim(...).getFirst()} to grab "the" row it just enqueued needs that first element to be deterministic,
     * not whichever one Postgres happened to materialize first.
     *
     * <p>
     * {@code @Transactional} - see this class's own javadoc for why plain {@code REQUIRED} is enough, and preferred
     * over {@code REQUIRES_NEW}, at every one of this class's call sites - so the claiming {@code UPDATE ... RETURNING}
     * commits on its own the moment this method returns, before {@link OutboxDrain} touches the first claimed row. A
     * row is therefore durably {@code SENDING} (or {@code ABANDONED}) the instant it is claimed, never rolled back by
     * anything that happens to a later row in the same batch - the claim and the send are two separate transactions.
     */
    @Transactional
    List<OutboxEntity> claim(ClaimQuery query) {

        var retryBefore = Timestamp.from(query.now().minus(query.retryBackoff()));
        var leaseExpiredBefore = Timestamp.from(query.now().minus(query.claimLease()));

        var updated = jdbc.query(CLAIM_SQL,
                new MapSqlParameterSource().addValue("sending", OutboxStatus.SENDING.name())
                        .addValue("abandoned", OutboxStatus.ABANDONED.name())
                        .addValue("now", Timestamp.from(query.now())).addValue("maxAttempts", query.maxAttempts())
                        .addValue("retryBefore", retryBefore).addValue("leaseExpiredBefore", leaseExpiredBefore)
                        .addValue("batch", query.batchSize()).addValue("mailHeld", query.mailHeld()),
                ROW_MAPPER);

        // The ABANDONED rows this same statement just terminalized (below) are a real, deliberate side effect, not
        // work for the caller: OutboxDrain must never try to render or send one. Filtering here, once, keeps that
        // invariant out of every caller's hands rather than trusting each one to re-check status before acting on it.
        return updated.stream().filter(row -> OutboxStatus.SENDING.name().equals(row.getStatus())).toList();
    }

    static final String CLAIM_SQL = """
            WITH claimed AS (
               UPDATE communication_delivery
                  SET status = CASE WHEN status = 'SENDING' AND attempts >= :maxAttempts THEN :abandoned
                                     ELSE :sending END,
                      claimed_at = :now,
                      updated_at = :now,
                      attempts = CASE WHEN status = 'SENDING' AND attempts < :maxAttempts THEN attempts + 1
                                       ELSE attempts END
                WHERE id IN (SELECT id FROM communication_delivery
                              WHERE (status = 'PENDING'
                                     OR (status = 'FAILED' AND attempts < :maxAttempts AND updated_at < :retryBefore)
                                     OR (status = 'SENDING' AND claimed_at < :leaseExpiredBefore))
                                AND organization_id = current_setting('app.current_org', true)
                                AND (channel <> 'EMAIL' OR NOT :mailHeld)
                              ORDER BY id
                              FOR UPDATE SKIP LOCKED
                              LIMIT :batch)
            RETURNING *)
            SELECT c.*, m.message_type, m.subject_ref, m.template, m.facts
              FROM claimed c
              JOIN communication_message m ON m.id = c.message_id
             ORDER BY c.id
            """;

    /**
     * A well-named parameter object rather than a six-parameter {@code claim}, which PMD would refuse. {@code mailHeld}
     * leaves every mail row where it is and claims only in-app and chat rows, for the rest of a tick in which the
     * server refused mail and accepted none of a batch (see {@link MailHold}).
     */
    @Builder
    record ClaimQuery(int batchSize, Instant now, int maxAttempts, Duration retryBackoff, Duration claimLease,
            boolean mailHeld) {
    }

    /** Records a successful send. */
    void markSent(Long id, String organizationId, Instant now) {

        markStatus(StatusUpdate.builder().id(id).organizationId(organizationId).now(now).status(OutboxStatus.SENT)
                .extraSet(", sent_at = :now").extraParams(new MapSqlParameterSource()).build());
    }

    /** Records a row the sweep deliberately did not send (recipient opted out, for instance). */
    void markSuppressed(Long id, String organizationId, String reason, Instant now) {

        markStatus(StatusUpdate.builder().id(id).organizationId(organizationId).now(now).status(OutboxStatus.SUPPRESSED)
                .extraSet(", suppressed_reason = :reason").extraParams(new MapSqlParameterSource("reason", reason))
                .build());
    }

    /**
     * Records a send attempt that failed and increments {@code attempts}. Not terminal on its own: {@link #claim}
     * widens past {@code PENDING} to also reclaim a {@code FAILED} row once {@code attempts} is still under
     * {@code maxAttempts} and {@code updated_at} - stamped here - is older than the configured backoff, moving it
     * straight back to {@code SENDING} without ever passing back through {@code PENDING}.
     *
     * <p>
     * Once the increment here reaches {@code maxAttempts}, the row becomes {@code ABANDONED} rather than staying
     * {@code FAILED}: a row {@link #claim}'s {@code FAILED} branch will never select again must not also sit forever in
     * {@code communication_delivery_claimable}'s partial index at its old, low {@code id} - every future
     * {@code claim}'s {@code ORDER BY id ... LIMIT} scan would pay to skip past it, permanently, and that cost only
     * grows as more rows exhaust their attempts. {@code claim}'s own {@code SENDING} branch enforces the identical cap
     * for the path that never reaches this method at all - a row that dies deterministically right after being claimed,
     * before any mark is ever written.
     *
     * <p>
     * {@code @Transactional}, plain {@code REQUIRED} - see this class's own javadoc for why - for the reason given
     * there: {@link OutboxDrain}'s catch block calls this method precisely because {@link OutboxRowProcessor#process}
     * threw, and that method's own transaction has already rolled back and released its connection by the time control
     * reaches here. Recording the failure must not, and now cannot, share a connection with whatever caused it - a
     * database error in {@code process} can abort that row's own transaction, never this one, and there is no ambient
     * transaction left at this call site for {@code REQUIRED} to join even if there were.
     */
    @Transactional
    void markFailed(Long id, String organizationId, Instant now, int maxAttempts) {

        jdbc.update("""
                UPDATE communication_delivery
                   SET attempts = attempts + 1,
                       updated_at = :now,
                       status = CASE WHEN attempts + 1 >= :maxAttempts THEN :abandoned ELSE :failed END
                 WHERE id = :id AND organization_id = :org
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now))
                .addValue("maxAttempts", maxAttempts).addValue("abandoned", OutboxStatus.ABANDONED.name())
                .addValue("failed", OutboxStatus.FAILED.name()).addValue("id", id).addValue("org", organizationId));
    }

    /**
     * Best-effort: releases rows a shutting-down drain claimed but never reached back to {@code PENDING} immediately,
     * rather than leaving them {@code SENDING} for the full {@code claimLease}. Makes a graceful redeploy free rather
     * than merely survivable - the released rows are claimable again on the very next tick instead of after the lease
     * expires. Guarded by {@code status = 'SENDING'} so a row concurrently reclaimed, or otherwise moved, by another
     * process in the meantime is left exactly as that other process left it.
     */
    int release(List<Long> ids, String organizationId, Instant now) {

        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.update("""
                UPDATE communication_delivery
                   SET status = :pending, updated_at = :now
                 WHERE id IN (:ids) AND organization_id = :org AND status = :sending
                """,
                new MapSqlParameterSource().addValue("pending", OutboxStatus.PENDING.name())
                        .addValue("now", Timestamp.from(now)).addValue("ids", ids).addValue("org", organizationId)
                        .addValue("sending", OutboxStatus.SENDING.name()));
    }

    /** A well-named parameter object rather than a six-parameter {@code markStatus}, which PMD would refuse. */
    @Builder
    private record StatusUpdate(Long id, String organizationId, Instant now, OutboxStatus status, String extraSet,
            MapSqlParameterSource extraParams) {
    }

    private void markStatus(StatusUpdate update) {

        jdbc.update(
                "UPDATE communication_delivery SET status = :status, updated_at = :now" + update.extraSet()
                        + " WHERE id = :id AND organization_id = :org",
                update.extraParams().addValue("status", update.status().name())
                        .addValue("now", Timestamp.from(update.now())).addValue("id", update.id())
                        .addValue("org", update.organizationId()));
    }

    private static OutboxEntity mapRow(ResultSet rs, int rowNum) throws SQLException {

        return OutboxEntity.builder().id(rs.getLong("id")).messageId(rs.getLong("message_id"))
                .organizationId(rs.getString("organization_id")).messageType(rs.getString("message_type"))
                .subjectRef(rs.getString("subject_ref")).userId(rs.getString("user_id"))
                .channel(rs.getString("channel")).chatDestinationId(rs.getObject("chat_destination_id", Long.class))
                .template(rs.getString("template")).facts(rs.getString("facts")).status(rs.getString("status"))
                .attempts(rs.getInt("attempts")).claimedAt(toInstant(rs, "claimed_at")).sentAt(toInstant(rs, "sent_at"))
                .suppressedReason(rs.getString("suppressed_reason")).createdAt(toInstant(rs, "created_at"))
                .updatedAt(toInstant(rs, "updated_at")).version(rs.getLong("version")).build();
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {

        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
