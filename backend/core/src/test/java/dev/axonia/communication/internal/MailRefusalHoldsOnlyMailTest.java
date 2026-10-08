package dev.axonia.communication.internal;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;

import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.eclipse.angus.mail.util.SocketConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import dev.axonia.IntegrationMetrics;
import dev.axonia.UsageCounters;
import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailRefusal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A refused mail server, through the real drain, workers and SMTP adapter: a refused sender and a refused connection,
 * in a batch whose mail the server accepted none of, both stop the organization's mail for the rest of the sweep, and
 * nothing else. Its in-app entries and chat posts queued behind the held mail go out in the same sweep, and the held
 * mail goes out on the next one with no attempt spent on it.
 *
 * <p>
 * The outbox is an in-memory queue claiming the way {@link JdbcOutbox#CLAIM_SQL} does - pending rows, oldest first, a
 * batch at a time, mail left out once it is held - which {@code JdbcOutboxTest} pins against Postgres. Two rows a claim
 * and one worker, so the held mail fills the whole first claim: the in-app entry and the chat post are reached only
 * because the drain claims again with the mail held, and would otherwise starve behind it.
 */
@Timeout(10)
class MailRefusalHoldsOnlyMailTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ORGANIZATION_ID = "acme";
    private static final OutgoingMail MAIL = new OutgoingMail("member@example.com", "Subject", "<p>html</p>", "text",
            null);

    private final RecordingMailServer mailServer = RecordingMailServer.configured(RecordingMailServer.LOCAL);
    private final RefusingTransport transport = new RefusingTransport();
    private final CountingTransports transports = new CountingTransports(transport);
    private final Queue queue = new Queue();
    private final JdbcOutbox outbox = mock(JdbcOutbox.class);
    private final SendPacing pacing = mock(SendPacing.class);

    private OutboxDrain drain;

    @BeforeEach
    void wireTheRealDrainPath() {

        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var registry = new SimpleMeterRegistry();
        var delivery = new SmtpDelivery(mailServer, new MailSenders(), transports, new IntegrationMetrics(registry),
                new UsageCounters(registry));
        var workers = new DrainWorkers((row, batch) -> {
            if (DeliveryChannel.EMAIL.name().equals(row.getChannel())) {
                batch.send(MAIL);
            }
            queue.markSent(row);
            return Optional.empty();
        }, 1, new StubChatDestinationsPort(), clock);
        var properties = new CommunicationProperties(2, Duration.ofSeconds(45), 10, 1, 5, Duration.ofMinutes(5),
                Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(30), Duration.ofDays(90), 200,
                "test-unsubscribe-secret");
        drain = new OutboxDrain(outbox, workers, delivery, clock, properties, pacing);

        when(pacing.awaitSendSlot(any(), any())).thenReturn(true);
        when(outbox.claim(any(JdbcOutbox.ClaimQuery.class)))
                .thenAnswer(invocation -> queue.claim(invocation.getArgument(0)));
        doAnswer(invocation -> queue.release(invocation.getArgument(0))).when(outbox).release(any(), any(), any());

        queue.add(1L, DeliveryChannel.EMAIL);
        queue.add(2L, DeliveryChannel.EMAIL);
        queue.add(3L, DeliveryChannel.IN_APP);
        queue.add(4L, DeliveryChannel.CHAT);
    }

    @Test
    void aRefusedSenderHoldsTheMailWhileTheInAppEntryAndTheChatPostBehindItGoInTheSameSweep() {

        transport.refuseEveryMessageWith(new SMTPSendFailedException("MAIL FROM:<axonia@localhost>", 550,
                "550 5.7.1 Sender address rejected: not owned by user\n", null, null, null, null));

        drain.drain(NOW.plus(Duration.ofSeconds(45)));

        assertThat(queue.statuses()).isEqualTo(Map.of(1L, "PENDING", 2L, "PENDING", 3L, "SENT", 4L, "SENT"));
        assertThat(transport.attempts).hasValue(1);
        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.SENDER_REFUSED,
                Optional.of("550 5.7.1 Sender address rejected: not owned by user")));
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
    }

    @Test
    void aRefusedConnectionHoldsTheMailWhileTheInAppEntryAndTheChatPostBehindItGoInTheSameSweep() {

        transports.refuseWith(new MailConnectException(new SocketConnectException("Using SocketFactory class",
                new ConnectException("Connection refused"), "smtp.acme.invalid", 587, 2_000)));

        drain.drain(NOW.plus(Duration.ofSeconds(45)));

        assertThat(queue.statuses()).isEqualTo(Map.of(1L, "PENDING", 2L, "PENDING", 3L, "SENT", 4L, "SENT"));
        assertThat(transports.connections).hasValue(1);
        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
    }

    @Test
    void theHeldMailGoesOnTheNextSweepWithNoAttemptSpentOnIt() {

        transport.refuseEveryMessageWith(new SMTPSendFailedException("MAIL FROM:<axonia@localhost>", 550,
                "550 5.7.1 Sender address rejected: not owned by user\n", null, null, null, null));
        drain.drain(NOW.plus(Duration.ofSeconds(45)));
        transport.acceptAgain();

        drain.drain(NOW.plus(Duration.ofSeconds(45)));

        assertThat(queue.statuses()).isEqualTo(Map.of(1L, "SENT", 2L, "SENT", 3L, "SENT", 4L, "SENT"));
        assertThat(mailServer.outcomes()).last().isEqualTo(MailOutcome.accepted());
        verify(outbox, never()).markFailed(anyLong(), any(), any(), anyInt());
    }

    /** Claims the way {@link JdbcOutbox#CLAIM_SQL} does, for the statuses this class moves rows through. */
    private static final class Queue {

        private final Map<Long, OutboxEntity> rows = new TreeMap<>();
        private final Map<Long, String> statuses = new TreeMap<>();

        void add(long id, DeliveryChannel channel) {

            rows.put(id, OutboxEntity.builder().id(id).organizationId(ORGANIZATION_ID).messageType("session_wrap_up")
                    .template("session-wrap-up").channel(channel.name()).status("PENDING").build());
            statuses.put(id, "PENDING");
        }

        synchronized List<OutboxEntity> claim(JdbcOutbox.ClaimQuery query) {

            var claimed = rows.values().stream().filter(row -> "PENDING".equals(statuses.get(row.getId())))
                    .filter(row -> !query.mailHeld() || !DeliveryChannel.EMAIL.name().equals(row.getChannel()))
                    .limit(query.batchSize()).toList();
            claimed.forEach(row -> statuses.put(row.getId(), "SENDING"));
            return claimed;
        }

        synchronized int release(List<Long> ids) {

            ids.forEach(id -> statuses.put(id, "PENDING"));
            return ids.size();
        }

        synchronized void markSent(OutboxEntity row) {

            statuses.put(row.getId(), "SENT");
        }

        synchronized Map<Long, String> statuses() {

            return new TreeMap<>(statuses);
        }
    }

    private static final class CountingTransports implements TransportFactory {

        private final Transport transport;
        private final AtomicInteger connections = new AtomicInteger();
        private MessagingException refusal;

        private CountingTransports(Transport transport) {
            this.transport = transport;
        }

        void refuseWith(MessagingException refusal) {

            this.refusal = refusal;
        }

        @Override
        public Transport connected(JavaMailSenderImpl sender) throws MessagingException {

            connections.incrementAndGet();
            if (refusal != null) {
                throw refusal;
            }
            return transport;
        }
    }

    /** Speaks to no socket; stays connected, so a refusal it raises is the server's answer, not a dropped line. */
    private static final class RefusingTransport extends Transport {

        private final AtomicInteger attempts = new AtomicInteger();
        private volatile MessagingException refusal;

        private RefusingTransport() {
            super(Session.getInstance(new Properties()), null);
            setConnected(true);
        }

        void refuseEveryMessageWith(MessagingException refusal) {

            this.refusal = refusal;
        }

        void acceptAgain() {

            refusal = null;
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) throws MessagingException {

            attempts.incrementAndGet();
            if (refusal != null) {
                throw refusal;
            }
        }

        /** Stays connected, as the real one would for the next batch's fresh connection. */
        @Override
        public void close() {
        }
    }
}
