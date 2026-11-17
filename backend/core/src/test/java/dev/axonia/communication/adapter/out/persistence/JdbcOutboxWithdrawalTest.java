package dev.axonia.communication.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessageMother;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sender takes back a message nobody was handed yet; a delivery already made, or on its way, stays as it is.
 */
class JdbcOutboxWithdrawalTest extends CommunicationJdbcTestSupport {

    @Autowired
    private JdbcOutbox outbox;
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
    void everyDeliveryNotYetMadeIsWithdrawn() {

        // given
        outbox.enqueue(OutboundMessageMother.any().subjectRef("w-1").recipientUserIds(List.of("alice", "bob")).build());
        setStatus("w-1", "bob", "FAILED");

        // when
        var withdrawn = outbox.withdrawPending(MessageType.SESSION_WRAP_UP, "w-1");

        // then
        assertThat(withdrawn).isEqualTo(4);
        assertThat(statuses("w-1")).containsOnly("SUPPRESSED:WITHDRAWN");
    }

    @Test
    void aDeliveryAlreadySentOrInFlightStaysAsItIs() {

        // given
        outbox.enqueue(OutboundMessageMother.any().subjectRef("w-2").recipientUserIds(List.of("alice", "bob"))
                .inAppOnly(true).build());
        setStatus("w-2", "alice", "SENT");
        setStatus("w-2", "bob", "SENDING");

        // when
        var withdrawn = outbox.withdrawPending(MessageType.SESSION_WRAP_UP, "w-2");

        // then
        assertThat(withdrawn).isZero();
        assertThat(statuses("w-2")).containsExactlyInAnyOrder("SENT:", "SENDING:");
    }

    @Test
    void anotherSubjectOrTypeIsLeftAlone() {

        // given
        outbox.enqueue(OutboundMessageMother.any().subjectRef("w-3").inAppOnly(true).build());
        outbox.enqueue(OutboundMessageMother.any().type(MessageType.SESSION_UPCOMING).subjectRef("w-4").inAppOnly(true)
                .build());

        // when
        var otherSubject = outbox.withdrawPending(MessageType.SESSION_WRAP_UP, "w-4");
        var otherType = outbox.withdrawPending(MessageType.SESSION_UPCOMING, "w-3");

        // then
        assertThat(otherSubject + otherType).isZero();
        assertThat(statuses("w-3")).containsExactly("PENDING:");
        assertThat(statuses("w-4")).containsExactly("PENDING:");
    }

    @Test
    void anotherOrganizationsMessageIsLeftAlone() {

        // given
        outbox.enqueue(OutboundMessageMother.any().subjectRef("w-5").inAppOnly(true).build());
        jdbc.update("UPDATE communication_message SET organization_id = 'globex' WHERE subject_ref = 'w-5'",
                new MapSqlParameterSource());
        jdbc.update("""
                UPDATE communication_delivery SET organization_id = 'globex'
                 WHERE message_id IN (SELECT id FROM communication_message WHERE subject_ref = 'w-5')
                """, new MapSqlParameterSource());

        // when
        var withdrawn = outbox.withdrawPending(MessageType.SESSION_WRAP_UP, "w-5");

        // then
        assertThat(withdrawn).isZero();
    }

    private void setStatus(String subjectRef, String userId, String status) {

        jdbc.update("""
                UPDATE communication_delivery d SET status = :status
                  FROM communication_message m
                 WHERE m.id = d.message_id AND m.subject_ref = :subject AND d.user_id = :user
                   AND d.organization_id = 'acme'
                """, new MapSqlParameterSource().addValue("status", status).addValue("subject", subjectRef)
                .addValue("user", userId));
    }

    private List<String> statuses(String subjectRef) {

        return jdbc.queryForList("""
                SELECT d.status || ':' || coalesce(d.suppressed_reason, '')
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE m.subject_ref = :subject AND d.organization_id = 'acme'
                """, new MapSqlParameterSource("subject", subjectRef), String.class);
    }
}
