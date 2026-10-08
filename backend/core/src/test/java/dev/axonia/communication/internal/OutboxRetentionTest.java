package dev.axonia.communication.internal;

import java.sql.Timestamp;
import java.time.Instant;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ninety-day pass over the outbox: a message with only terminal deliveries older than the cutoff is expired all at
 * once, a message with even one non-terminal delivery is left entirely alone, and re-running the pass over an
 * already-expired message is a no-op rather than an error.
 *
 * <p>
 * {@link #theExpiredAtStampIsNowNotCutoff} pins that {@link OutboxRetention#expire} takes {@code now} and
 * {@code cutoff} as two distinct instants, and {@link ExpiredMessages#record} is stamped with {@code now} - when the
 * record was written - never with {@code cutoff}, which is ninety days in the past by construction. Every other test in
 * this class calls {@code expire} with {@code NOW} and {@code CUTOFF} both fixed so the two can never accidentally
 * coincide and mask that distinction.
 */
class OutboxRetentionTest extends CommunicationJdbcTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final Instant NINETY_ONE_DAYS_AGO = Instant.parse("2026-05-04T00:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-05-15T00:00:00Z");

    @Autowired
    private OutboxRetention retention;
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

    /**
     * Asserts only on subject {@code 501}, never on {@code expire}'s returned aggregate count: this class's own
     * {@code acme} scope is shared with whatever else the suite - now or later - commits under that organization id,
     * and a repo with a documented history of suite-order failures (a test reading what another test wrote) is not one
     * where "the aggregate happened to be exactly 1" is a safe thing to assert. The subject-scoped checks below prove
     * the same fact - this message and only this message was expired - without depending on nothing else in
     * {@code acme} ever being eligible at the same time.
     */
    @Test
    void aMessageOlderThanTheCutoffWithOnlyTerminalRowsIsExpired() {

        var messageId = insertMessage("501", NINETY_ONE_DAYS_AGO);
        insertDelivery(messageId, "alice", "EMAIL", "SENT", null);
        insertDelivery(messageId, "alice", "IN_APP", "SENT", null);

        retention.expire(NOW, CUTOFF, 100);

        assertThat(countMessages("501")).isZero();
        assertThat(countDeliveries(messageId)).isZero();
        assertThat(expiredRecordSentCount("501")).isEqualTo(1);
    }

    @Test
    void aMessageWithOneNonTerminalRowIsLeftEntirelyAlone() {

        var messageId = insertMessage("502", NINETY_ONE_DAYS_AGO);
        insertDelivery(messageId, "alice", "EMAIL", "SENT", null);
        insertDelivery(messageId, "bob", "EMAIL", "FAILED", null);

        retention.expire(NOW, CUTOFF, 100);

        assertThat(countMessages("502")).isEqualTo(1);
        assertThat(countDeliveries(messageId)).isEqualTo(2);
        assertThat(countExpiredRecords("502")).isZero();
    }

    @Test
    void aMessageYoungerThanTheCutoffIsLeftAlone() {

        var messageId = insertMessage("503", Instant.parse("2026-08-01T00:00:00Z"));
        insertDelivery(messageId, "alice", "EMAIL", "SENT", null);

        retention.expire(NOW, CUTOFF, 100);

        assertThat(countMessages("503")).isEqualTo(1);
        assertThat(countDeliveries(messageId)).isEqualTo(1);
    }

    @Test
    void theRecordCountsOnlyMailRowsAndSeparatesSuppressionFromUnreachable() {

        var messageId = insertMessage("504", NINETY_ONE_DAYS_AGO);
        insertDelivery(messageId, "alice", "EMAIL", "SENT", null);
        insertDelivery(messageId, "bob", "EMAIL", "SUPPRESSED", "MEMBER_OPTED_OUT");
        insertDelivery(messageId, "carol", "EMAIL", "SUPPRESSED", "NO_ADDRESS");
        insertDelivery(messageId, "alice", "IN_APP", "SENT", null);

        retention.expire(NOW, CUTOFF, 100);

        assertThat(expiredRecordSentCount("504")).isEqualTo(1);
        assertThat(expiredRecordSuppressedCount("504")).isEqualTo(1);
        assertThat(expiredRecordUnreachableCount("504")).isEqualTo(1);
    }

    /**
     * A no-op second pass over the SAME already-deleted message would never reach
     * {@code ON CONFLICT (organization_id, message_type, subject_ref) DO NOTHING} at all - {@code candidates} would
     * simply find nothing left to select, and {@link ExpiredMessages#record} would never be called a second time. To
     * genuinely exercise that arm, a second message for the SAME {@code (message_type, subject_ref)} is inserted after
     * the first has already expired - legal now that the first row is gone, since the unique constraint on
     * {@code communication_message} scopes to exactly that triple - with a DIFFERENT delivery mix (two {@code SENT}
     * rather than one) so a broken conflict handling (a naive insert throwing a unique violation, or an
     * {@code ON CONFLICT DO UPDATE} silently overwriting the counts) would be visible: the record must still read
     * {@code sent = 1}, the first pass's count, not {@code 2}. The second message is still deleted regardless - the
     * conflict is on the RECORD, not on the message row disappearing, which is the "deliberately deferred" contract:
     * {@code expire} does not distinguish "recorded and deleted" from "conflicted and deleted anyway".
     */
    @Test
    void expiringIsIdempotentAcrossRepeatedPasses() {

        var firstMessageId = insertMessage("505", NINETY_ONE_DAYS_AGO);
        insertDelivery(firstMessageId, "alice", "EMAIL", "SENT", null);
        retention.expire(NOW, CUTOFF, 100);

        assertThat(countExpiredRecords("505")).isEqualTo(1);
        assertThat(expiredRecordSentCount("505")).isEqualTo(1);

        var secondMessageId = insertMessage("505", NINETY_ONE_DAYS_AGO);
        insertDelivery(secondMessageId, "alice", "EMAIL", "SENT", null);
        insertDelivery(secondMessageId, "bob", "EMAIL", "SENT", null);

        retention.expire(NOW, CUTOFF, 100);

        assertThat(countMessages("505")).isZero();
        assertThat(countDeliveries(secondMessageId)).isZero();
        assertThat(countExpiredRecords("505")).isEqualTo(1);
        assertThat(expiredRecordSentCount("505")).isEqualTo(1);
    }

    @Test
    void theExpiredAtStampIsNowNotCutoff() {

        var messageId = insertMessage("506", NINETY_ONE_DAYS_AGO);
        insertDelivery(messageId, "alice", "EMAIL", "SENT", null);

        retention.expire(NOW, CUTOFF, 100);

        assertThat(expiredAt("506")).isEqualTo(NOW);
    }

    private Long insertMessage(String subjectRef, Instant createdAt) {

        return jdbc.queryForObject(
                """
                        INSERT INTO communication_message
                            (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                        VALUES ('acme', 'session_wrap_up', :subject, 'session-wrap-up', CAST('{}' AS jsonb), :created, :created, 0)
                        RETURNING id
                        """,
                new MapSqlParameterSource().addValue("subject", subjectRef).addValue("created",
                        Timestamp.from(createdAt)),
                Long.class);
    }

    private void insertDelivery(Long messageId, String userId, String channel, String status, String suppressedReason) {

        jdbc.update("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, channel, status, suppressed_reason, created_at, updated_at,
                     version)
                VALUES ('acme', :message, :user, :channel, :status, :reason, :now, :now, 0)
                """,
                new MapSqlParameterSource().addValue("message", messageId).addValue("user", userId)
                        .addValue("channel", channel).addValue("status", status).addValue("reason", suppressedReason)
                        .addValue("now", Timestamp.from(NOW)));
    }

    private long countMessages(String subjectRef) {

        var count = jdbc.queryForObject(
                "SELECT count(*) FROM communication_message WHERE organization_id = 'acme' AND subject_ref = :subject",
                Map.of("subject", subjectRef), Long.class);
        return count == null ? 0 : count;
    }

    private long countDeliveries(Long messageId) {

        var count = jdbc.queryForObject("SELECT count(*) FROM communication_delivery WHERE message_id = :message",
                Map.of("message", messageId), Long.class);
        return count == null ? 0 : count;
    }

    private long countExpiredRecords(String subjectRef) {

        var count = jdbc.queryForObject("""
                SELECT count(*) FROM communication_expired_message
                 WHERE organization_id = 'acme' AND subject_ref = :subject
                """, Map.of("subject", subjectRef), Long.class);
        return count == null ? 0 : count;
    }

    private int expiredRecordSentCount(String subjectRef) {

        return jdbc.queryForObject("""
                SELECT sent_count FROM communication_expired_message
                 WHERE organization_id = 'acme' AND subject_ref = :subject
                """, Map.of("subject", subjectRef), Integer.class);
    }

    private int expiredRecordSuppressedCount(String subjectRef) {

        return jdbc.queryForObject("""
                SELECT suppressed_count FROM communication_expired_message
                 WHERE organization_id = 'acme' AND subject_ref = :subject
                """, Map.of("subject", subjectRef), Integer.class);
    }

    private int expiredRecordUnreachableCount(String subjectRef) {

        return jdbc.queryForObject("""
                SELECT unreachable_count FROM communication_expired_message
                 WHERE organization_id = 'acme' AND subject_ref = :subject
                """, Map.of("subject", subjectRef), Integer.class);
    }

    private Instant expiredAt(String subjectRef) {

        var stamp = jdbc.queryForObject("""
                SELECT expired_at FROM communication_expired_message
                 WHERE organization_id = 'acme' AND subject_ref = :subject
                """, Map.of("subject", subjectRef), Timestamp.class);
        return stamp.toInstant();
    }
}
