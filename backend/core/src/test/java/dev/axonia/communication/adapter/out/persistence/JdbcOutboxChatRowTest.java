package dev.axonia.communication.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.communication.application.DeliveryGate;
import dev.axonia.communication.domain.OutboxDelivery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model of {@link JdbcOutboxTest}'s own {@code authenticate}/{@code clearSecurityContext} pair - see that class's
 * javadoc for why {@code @BeforeTransaction}, not {@code @BeforeEach}. {@link #FACTS} is arbitrary; nothing here reads
 * it back.
 *
 * <p>
 * {@link #expandingWritesOneDeliveryPerDestination}, {@link #expandingTwiceLeavesExactlyOneRowPerDestination} and
 * {@link #aDestinationAddedAfterEnqueueStillReceivesTheQueuedMessage} exercise {@link JdbcOutbox#expandChatRow}
 * directly against the real database, the same way {@link JdbcOutboxTest#reload} exercises {@code claim} - there is no
 * substitute for proving the {@code ON CONFLICT} arbiter actually matches the index the migration created.
 */
class JdbcOutboxChatRowTest extends CommunicationJdbcTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final Map<String, Object> FACTS = Map.of("sessionTitle", "Frontend Dojo");

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

    /**
     * Each of the 2 member recipients gets a mail row AND an in-app row (see {@link JdbcOutboxInAppRowTest}), beside
     * the community's own single chat row.
     */
    @Test
    void enqueue_writesOneChatRowBesideTheMemberRows() {

        whenChannelTypesAre(Set.of(MessageType.SESSION_WRAP_UP));

        outbox.enqueue(OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP).subjectRef("41")
                .recipientUserIds(List.of("u1", "u2")).template("mail/session-wrap-up").facts(FACTS).build());

        assertThat(deliveryChannels("41")).containsExactlyInAnyOrder("EMAIL", "EMAIL", "IN_APP", "IN_APP", "CHAT");
    }

    /** The recipient's second row is their in-app one; the type still writes no chat row. */
    @Test
    void enqueue_writesNoChatRowForATypeThatDoesNotPostToAChannel() {

        whenChannelTypesAre(Set.of(MessageType.SESSION_WRAP_UP));

        outbox.enqueue(OutboundMessage.builder().type(MessageType.SESSION_AWAITING_CLOSE).subjectRef("42")
                .recipientUserIds(List.of("u1")).template("mail/session-awaiting-close").facts(FACTS).build());

        assertThat(deliveryChannels("42")).containsExactlyInAnyOrder("EMAIL", "IN_APP");
    }

    /** The recipient's second row is their in-app one; a re-run still leaves exactly one chat row. */
    @Test
    void enqueue_reRunLeavesExactlyOneChatRow() {

        whenChannelTypesAre(Set.of(MessageType.SESSION_WRAP_UP));
        var message = OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP).subjectRef("43")
                .recipientUserIds(List.of("u1")).template("mail/session-wrap-up").facts(FACTS).build();

        outbox.enqueue(message);
        outbox.enqueue(message);

        assertThat(deliveryChannels("43")).containsExactlyInAnyOrder("EMAIL", "IN_APP", "CHAT");
    }

    @Test
    void enqueue_withNoRecipientsStillWritesTheChatRow() {

        whenChannelTypesAre(Set.of(MessageType.SESSION_WRAP_UP));

        outbox.enqueue(OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP).subjectRef("44")
                .recipientUserIds(List.of()).template("mail/session-wrap-up").facts(FACTS).build());

        assertThat(deliveryChannels("44")).containsExactly("CHAT");
    }

    /**
     * A sender reading "delivered: 1" must mean one person. The chat row is a forty-second recipient to nobody, and
     * this is the only test that would notice it becoming one.
     */
    @Test
    void aChatRowDoesNotEnterTheSendersRecipientCounts() {

        whenChannelTypesAre(Set.of(MessageType.SESSION_WRAP_UP));
        outbox.enqueue(OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP).subjectRef("45")
                .recipientUserIds(List.of("u1")).template("mail/session-wrap-up").facts(FACTS).build());

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, "45");

        assertThat(outcome.pending()).isEqualTo(1);
    }

    /**
     * The branch {@link DeliveryGate#admit} makes unreachable in production - a placeholder it lets through always has
     * at least one destination - still needs its own direct proof: a caller handed an empty list writes nothing rather
     * than an empty {@code batchUpdate} throwing or silently no-op-ing for the wrong reason.
     */
    @Test
    void expandingWithNoDestinationsWritesNothingAndReturnsZero() {

        var placeholder = enqueueChatPlaceholder();

        var written = outbox.expandChatRow(placeholder, List.of(), NOW);

        assertThat(written).isZero();
        assertThat(chatRowsFor(placeholder.getMessageId())).hasSize(1);
    }

    @Test
    void expandingWritesOneDeliveryPerDestination() {

        var placeholder = enqueueChatPlaceholder();

        var written = outbox.expandChatRow(placeholder, List.of(7L, 9L), NOW);

        assertThat(written).isEqualTo(2);
        assertThat(chatRowsFor(placeholder.getMessageId())).extracting(OutboxDelivery::getChatDestinationId)
                .containsExactlyInAnyOrder(null, 7L, 9L);
    }

    @Test
    void expandingTwiceLeavesExactlyOneRowPerDestination() {

        var placeholder = enqueueChatPlaceholder();
        outbox.expandChatRow(placeholder, List.of(7L, 9L), NOW);

        var written = outbox.expandChatRow(placeholder, List.of(7L, 9L), NOW);

        assertThat(written).isZero();
        assertThat(chatRowsFor(placeholder.getMessageId())).hasSize(3);
    }

    /**
     * The property drain-time expansion exists for, pinned directly: a destination that did not exist when the message
     * was enqueued still receives it, because expansion resolves destinations at drain rather than at enqueue.
     */
    @Test
    void aDestinationAddedAfterEnqueueStillReceivesTheQueuedMessage() {

        var placeholder = enqueueChatPlaceholder();

        // The destination did not exist at enqueue. It does now, at drain.
        var written = outbox.expandChatRow(placeholder, List.of(11L), NOW);

        assertThat(written).isEqualTo(1);
        assertThat(chatRowsFor(placeholder.getMessageId())).extracting(OutboxDelivery::getChatDestinationId)
                .contains(11L);
    }

    /**
     * Writes the placeholder the way {@link #enqueue_writesOneChatRowBesideTheMemberRows} already proves, then reads it
     * back through {@link JdbcOutbox#ROW_MAPPER} - the same join {@link JdbcOutboxTest#reload} uses - so the returned
     * {@link OutboxDelivery} carries the real {@code messageId} {@link JdbcOutbox#expandChatRow} needs.
     */
    private OutboxDelivery enqueueChatPlaceholder() {

        var subjectRef = "chat-placeholder";
        whenChannelTypesAre(Set.of(MessageType.SESSION_UPCOMING));
        outbox.enqueue(OutboundMessage.builder().type(MessageType.SESSION_UPCOMING).subjectRef(subjectRef)
                .recipientUserIds(List.of()).template("chat/session-upcoming").facts(FACTS).build());
        return chatRowsFor(subjectRef).get(0);
    }

    private List<OutboxDelivery> chatRowsFor(Long messageId) {

        return jdbc.query("""
                SELECT d.*, m.message_type, m.subject_ref, m.template, m.facts
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE d.message_id = :messageId AND d.channel = 'CHAT'
                """, Map.of("messageId", messageId), JdbcOutbox.ROW_MAPPER);
    }

    private List<OutboxDelivery> chatRowsFor(String subjectRef) {

        return jdbc.query("""
                SELECT d.*, m.message_type, m.subject_ref, m.template, m.facts
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE m.subject_ref = :subject AND d.channel = 'CHAT'
                """, Map.of("subject", subjectRef), JdbcOutbox.ROW_MAPPER);
    }
}
