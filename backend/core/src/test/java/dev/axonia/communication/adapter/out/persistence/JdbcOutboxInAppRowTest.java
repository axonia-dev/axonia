package dev.axonia.communication.adapter.out.persistence;

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

import dev.axonia.communication.api.OutboundMessageMother;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every message type gains an in-app row per recipient, unconditionally - there is no preference, no eligibility and no
 * opt-in gating it, because the panel is a complete record rather than a channel a member chooses.
 */
class JdbcOutboxInAppRowTest extends CommunicationJdbcTestSupport {

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
    void everyRecipientGetsAMailRowAndAnInAppRow() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("301").recipientUserIds(List.of("alice", "bob")).build());

        assertThat(countByChannel("301", "EMAIL")).isEqualTo(2);
        assertThat(countByChannel("301", "IN_APP")).isEqualTo(2);
    }

    @Test
    void theReturnedCountIncludesTheInAppRows() {

        var inserted = outbox.enqueue(
                OutboundMessageMother.any().subjectRef("302").recipientUserIds(List.of("alice", "bob")).build());

        assertThat(inserted).isEqualTo(4);
    }

    @Test
    void reEnqueueingTheSameMessageAddsNothing() {

        var message = OutboundMessageMother.any().subjectRef("303").recipientUserIds(List.of("alice")).build();
        outbox.enqueue(message);

        var second = outbox.enqueue(message);

        assertThat(second).isZero();
        assertThat(countByChannel("303", "IN_APP")).isEqualTo(1);
    }

    @Test
    void inAppOnlyWritesNoEmailRow() {

        var message = OutboundMessageMother.any().subjectRef("305").recipientUserIds(List.of("alice", "bob"))
                .inAppOnly(true).build();

        var inserted = outbox.enqueue(message);
        var again = outbox.enqueue(message);

        assertThat(inserted).isEqualTo(2);
        assertThat(again).isZero();
        assertThat(countByChannel("305", "IN_APP")).isEqualTo(2);
        assertThat(countByChannel("305", "EMAIL")).isZero();
    }

    @Test
    void aMessageAboutAMemberNamesThemBesideTheSendersFacts() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("306").aboutUserId("lea").build());

        assertThat(storedFact("306", "aboutUserId")).isEqualTo("lea");
        assertThat(storedFact("306", "sessionTitle")).isEqualTo("Frontend Dojo");
    }

    @Test
    void aMessageAboutNobodyStoresTheSendersFactsAlone() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("307").build());

        assertThat(storedFact("307", "aboutUserId")).isNull();
        assertThat(storedFact("307", "sessionTitle")).isEqualTo("Frontend Dojo");
    }

    @Test
    void aMessageWithNoRecipientsWritesNoInAppRows() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("304").recipientUserIds(List.of()).build());

        assertThat(countByChannel("304", "IN_APP")).isZero();
    }

    private String storedFact(String subjectRef, String fact) {

        return jdbc.queryForObject(
                "SELECT facts->>:fact FROM communication_message WHERE subject_ref = :subject AND organization_id = 'acme'",
                Map.of("subject", subjectRef, "fact", fact), String.class);
    }

    /**
     * Scoped to {@code acme} - see {@link JdbcOutboxTest#countRows}'s own javadoc for why an unscoped count is not safe
     * on the shared {@code jdbc:tc} container this package's {@code @SpringBootTest} classes also commit real,
     * never-rolled-back rows to.
     */
    private Integer countByChannel(String subjectRef, String channel) {

        return jdbc.queryForObject("""
                SELECT count(*)
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE m.subject_ref = :subject AND d.channel = :channel AND d.organization_id = 'acme'
                """, new MapSqlParameterSource().addValue("subject", subjectRef).addValue("channel", channel),
                Integer.class);
    }
}
