package dev.axonia.communication.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import dev.axonia.communication.MessageOutcome;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.mother.OutboundMessageMother;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The record that survives a message's expiry. The guard against re-enqueueing an expired subject is proven here
 * through {@link JdbcOutbox#enqueue} itself, not through a method on {@link ExpiredMessages} - see
 * {@link #anExpiredSubjectCannotBeEnqueuedAgain} - because that guard lives inside {@link JdbcOutbox#upsertMessage}'s
 * own SQL now, folded atomically into the insert (see that method's own javadoc for why).
 * {@link ExpiredMessages#outcomeFor} is what keeps a sender's counts from silently becoming zero once the live rows are
 * gone, and {@link ExpiredMessages#record} is what fills this table in the first place.
 */
class ExpiredMessagesTest extends CommunicationJdbcTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Autowired
    private JdbcOutbox outbox;
    @Autowired
    private ExpiredMessages expiredMessages;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeTransaction
    void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void anExpiredSubjectCannotBeEnqueuedAgain() {

        insertExpired("401");

        var inserted = outbox
                .enqueue(OutboundMessageMother.any().subjectRef("401").recipientUserIds(List.of("alice")).build());

        assertThat(inserted).isZero();
        assertThat(countMessages("401")).isZero();
    }

    @Test
    void outcomeForRecoversAnExpiredSubjectsCounts() {

        insertExpired("402");

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, "402");

        assertThat(outcome.sent()).isEqualTo(41);
        assertThat(outcome.suppressed()).isEqualTo(3);
        assertThat(outcome.pending()).isZero();
    }

    @Test
    void outcomeForStillReportsAllZeroForASubjectNothingWasEverSentFor() {

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, "never-enqueued");

        assertThat(outcome.sent()).isZero();
        assertThat(outcome.suppressed()).isZero();
    }

    /**
     * The empty-list branch {@link ExpiredMessages#record} takes before it ever touches the database - the retention
     * pass calls it with whatever a page of already-terminal messages turns out to be, which can be zero.
     */
    @Test
    void recordWithNoMessageIdsIsANoOp() {

        assertThat(expiredMessages.record(List.of(), NOW)).isZero();
    }

    /**
     * {@link ExpiredMessages#record} counts only {@code EMAIL} rows, mirroring {@link JdbcOutbox#outcomeFor}'s own
     * filter - a message's in-app row must not inflate the "delivered" count a sender reads back once the message row
     * is gone. BOTH the mail row and the in-app row are marked {@code SENT} here, deliberately: with only the mail row
     * marked, {@code count(*) FILTER (WHERE d.channel = 'EMAIL' AND d.status = 'SENT')} and an unfiltered
     * {@code count(*) FILTER (WHERE d.status = 'SENT')} return the identical answer, so deleting the channel filter
     * would leave this assertion green. Marking both rows sent makes the two diverge - 1 with the filter, 2 without -
     * so a deleted filter turns this test red.
     */
    @Test
    void recordCountsOnlyEmailDeliveriesForTheMessage() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("501").recipientUserIds(List.of("user-1")).build());
        var claimed = claim();
        var mailRow = mailRowOf(claimed);
        var inAppRow = inAppRowOf(claimed);
        outbox.markSent(mailRow.getId(), mailRow.getOrganizationId(), NOW);
        outbox.markSent(inAppRow.getId(), inAppRow.getOrganizationId(), NOW);
        var messageId = messageIdFor("501");

        var written = expiredMessages.record(List.of(messageId), NOW);

        assertThat(written).isEqualTo(1);
        var outcome = expiredMessages.outcomeFor(MessageType.SESSION_WRAP_UP.code(), "501");
        assertThat(outcome).isPresent();
        assertThat(outcome.get().sent()).isEqualTo(1);
    }

    /**
     * All five {@link MessageOutcome} counts, driven to five DIFFERENT statuses through the exact same enqueue/claim/
     * mark sequence {@link JdbcOutbox} itself uses - see
     * {@link JdbcOutboxTest#outcomeForCountsEveryRowByItsCoarsenedStatus} for the identical shape against the live
     * path. JaCoCo cannot see into a single {@code jdbc.update}'s SQL text, so a transposed pair of columns in
     * {@link ExpiredMessages#record}'s {@code SELECT} list would still cover every line and branch; only an assertion
     * that reads every count back through {@link ExpiredMessages#outcomeFor} can catch that - and only if the four
     * counting expressions are driven to four DIFFERENT values. {@code sent} 1, {@code suppressed} 2,
     * {@code unreachable} 3, {@code abandoned} 4 (pending stays the literal 0 {@code record} always writes) are chosen
     * so no two are equal: every pairwise swap among the four - {@code sent}/{@code suppressed},
     * {@code sent}/{@code unreachable}, {@code sent}/{@code abandoned}, {@code suppressed}/{@code unreachable},
     * {@code suppressed}/{@code abandoned}, {@code unreachable}/{@code abandoned} - changes at least one field of the
     * asserted {@link MessageOutcome}, so this test goes red under every one of them. An earlier version of this test
     * used 1 for all four non-zero counts, which made {@code suppressed}/{@code unreachable} indistinguishable from a
     * swap; a second SUPPRESSED/NO_ADDRESS recipient and two more ABANDONED ones close that gap.
     */
    @Test
    void recordCapturesAllFiveOutcomeCountsFromTheLiveDeliveries() {

        var subjectRef = "503";

        markSent(mailRowFor(subjectRef, "user-sent"));

        markSuppressed(mailRowFor(subjectRef, "user-opted-out-1"), SuppressionReason.MEMBER_OPTED_OUT);
        markSuppressed(mailRowFor(subjectRef, "user-opted-out-2"), SuppressionReason.MEMBER_OPTED_OUT);

        markSuppressed(mailRowFor(subjectRef, "user-no-address-1"), SuppressionReason.NO_ADDRESS);
        markSuppressed(mailRowFor(subjectRef, "user-no-address-2"), SuppressionReason.NO_ADDRESS);
        markSuppressed(mailRowFor(subjectRef, "user-no-address-3"), SuppressionReason.NO_ADDRESS);

        abandon(mailRowFor(subjectRef, "user-abandoned-1"));
        abandon(mailRowFor(subjectRef, "user-abandoned-2"));
        abandon(mailRowFor(subjectRef, "user-abandoned-3"));
        abandon(mailRowFor(subjectRef, "user-abandoned-4"));

        var messageId = messageIdFor(subjectRef);

        var written = expiredMessages.record(List.of(messageId), NOW);

        assertThat(written).isEqualTo(1);
        var outcome = expiredMessages.outcomeFor(MessageType.SESSION_WRAP_UP.code(), subjectRef);
        assertThat(outcome).isPresent();
        assertThat(outcome.get()).isEqualTo(
                MessageOutcome.builder().pending(0).sent(1).suppressed(2).unreachable(3).abandoned(4).build());
    }

    @Test
    void recordCountsAMailRowThatMetNoMailServerAsUnreachable() {

        var subjectRef = "expired-no-mail-server-subject";

        markSuppressed(mailRowFor(subjectRef, "user-opted-out"), SuppressionReason.MEMBER_OPTED_OUT);
        markSuppressed(mailRowFor(subjectRef, "user-no-server"), SuppressionReason.MAIL_NOT_CONFIGURED);
        markSuppressed(mailRowFor(subjectRef, "user-no-address"), SuppressionReason.NO_ADDRESS);

        expiredMessages.record(List.of(messageIdFor(subjectRef)), NOW);

        assertThat(expiredMessages.outcomeFor(MessageType.SESSION_WRAP_UP.code(), subjectRef)).contains(
                MessageOutcome.builder().pending(0).sent(0).suppressed(1).unreachable(2).abandoned(0).build());
    }

    /** Enqueues one recipient under {@code subjectRef} and returns their claimed mail row, ready to mark. */
    private OutboxEntity mailRowFor(String subjectRef, String userId) {

        outbox.enqueue(OutboundMessageMother.any().subjectRef(subjectRef).recipientUserIds(List.of(userId)).build());
        return mailRowOf(claim());
    }

    private void markSent(OutboxEntity row) {

        outbox.markSent(row.getId(), row.getOrganizationId(), NOW);
    }

    private void markSuppressed(OutboxEntity row, SuppressionReason reason) {

        outbox.markSuppressed(row.getId(), row.getOrganizationId(), reason.name(), NOW);
    }

    /** {@code maxAttempts} of 1 abandons on the very first failed attempt. */
    private void abandon(OutboxEntity row) {

        outbox.markFailed(row.getId(), row.getOrganizationId(), NOW, 1);
    }

    /**
     * {@code ON CONFLICT (organization_id, message_type, subject_ref) DO NOTHING} is what makes the retention pass safe
     * to retry: a second call for the same subject, after the first already recorded it, writes nothing rather than
     * throwing a unique-violation.
     */
    @Test
    void recordIsANoOpForAnAlreadyRecordedSubject() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("502").recipientUserIds(List.of("user-1")).build());
        var messageId = messageIdFor("502");
        expiredMessages.record(List.of(messageId), NOW);

        var written = expiredMessages.record(List.of(messageId), NOW);

        assertThat(written).isZero();
    }

    private List<OutboxEntity> claim() {

        return outbox.claim(JdbcOutbox.ClaimQuery.builder().batchSize(10).now(NOW).maxAttempts(5)
                .retryBackoff(Duration.ofMinutes(5)).claimLease(Duration.ofMinutes(5)).build());
    }

    /**
     * {@code claim} does not distinguish channels, so a single recipient's batch also contains their in-app row - see
     * {@link JdbcOutboxTest#mailRowsOf} for the identical need in that class.
     */
    private static OutboxEntity mailRowOf(List<OutboxEntity> claimed) {

        return claimed.stream().filter(row -> DeliveryChannel.EMAIL.name().equals(row.getChannel())).findFirst()
                .orElseThrow();
    }

    private static OutboxEntity inAppRowOf(List<OutboxEntity> claimed) {

        return claimed.stream().filter(row -> DeliveryChannel.IN_APP.name().equals(row.getChannel())).findFirst()
                .orElseThrow();
    }

    private Long messageIdFor(String subjectRef) {

        return jdbc.queryForObject(
                "SELECT id FROM communication_message WHERE organization_id = 'acme' AND subject_ref = :subject",
                Map.of("subject", subjectRef), Long.class);
    }

    private void insertExpired(String subjectRef) {

        jdbc.update("""
                INSERT INTO communication_expired_message
                    (organization_id, message_type, subject_ref, pending_count, sent_count, suppressed_count,
                     unreachable_count, abandoned_count, expired_at)
                VALUES ('acme', 'session_wrap_up', :subject, 0, 41, 3, 0, 0, :now)
                """, new MapSqlParameterSource().addValue("subject", subjectRef).addValue("now", Timestamp.from(NOW)));
    }

    private long countMessages(String subjectRef) {

        var count = jdbc.queryForObject(
                "SELECT count(*) FROM communication_message WHERE organization_id = 'acme' AND subject_ref = :subject",
                Map.of("subject", subjectRef), Long.class);
        return count == null ? 0 : count;
    }
}
