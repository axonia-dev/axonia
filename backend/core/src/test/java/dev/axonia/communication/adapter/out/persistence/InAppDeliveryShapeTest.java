package dev.axonia.communication.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The schema half of the in-app channel: a member's mail row and their in-app row for the SAME message must coexist,
 * which the old per-recipient key forbade, and an in-app row must still be forced to name a person.
 */
class InAppDeliveryShapeTest extends CommunicationJdbcTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

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
    void aMemberHasBothAMailRowAndAnInAppRowForOneMessage() {

        var messageId = insertMessage("101");

        insertDelivery(messageId, "alice", "EMAIL");
        insertDelivery(messageId, "alice", "IN_APP");

        assertThat(countDeliveries(messageId)).isEqualTo(2);
    }

    @Test
    void aSecondInAppRowForTheSameMemberIsRefused() {

        var messageId = insertMessage("102");
        insertDelivery(messageId, "alice", "IN_APP");

        assertThatThrownBy(() -> insertDelivery(messageId, "alice", "IN_APP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInAppRowAddressedToNobodyIsRefused() {

        var messageId = insertMessage("103");

        assertThatThrownBy(() -> insertDelivery(messageId, null, "IN_APP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anExpiredMessageRecordIsUniquePerSubject() {

        insertExpired("201");

        assertThatThrownBy(() -> insertExpired("201")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertMessage(String subjectRef) {

        return jdbc.queryForObject("""
                INSERT INTO communication_message
                    (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                VALUES ('acme', 'session_wrap_up', :subject, 'session-wrap-up', CAST('{}' AS jsonb), :now, :now, 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("subject", subjectRef).addValue("now", Timestamp.from(NOW)),
                Long.class);
    }

    private void insertDelivery(Long messageId, String userId, String channel) {

        jdbc.update("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, channel, status, created_at, updated_at, version)
                VALUES ('acme', :message, :user, :channel, 'PENDING', :now, :now, 0)
                """, new MapSqlParameterSource().addValue("message", messageId).addValue("user", userId)
                .addValue("channel", channel).addValue("now", Timestamp.from(NOW)));
    }

    private void insertExpired(String subjectRef) {

        jdbc.update("""
                INSERT INTO communication_expired_message
                    (organization_id, message_type, subject_ref, pending_count, sent_count, suppressed_count,
                     unreachable_count, abandoned_count, expired_at)
                VALUES ('acme', 'session_wrap_up', :subject, 0, 41, 3, 0, 0, :now)
                """, new MapSqlParameterSource().addValue("subject", subjectRef).addValue("now", Timestamp.from(NOW)));
    }

    private Integer countDeliveries(Long messageId) {

        return jdbc.queryForObject("SELECT count(*) FROM communication_delivery WHERE message_id = :message",
                new MapSqlParameterSource().addValue("message", messageId), Integer.class);
    }
}
