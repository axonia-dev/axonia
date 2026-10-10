package dev.axonia.communication.adapter.out.mail;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.Test;

import dev.axonia.Mailpit;
import dev.axonia.communication.adapter.out.mail.MailSenders.SmtpTimeouts;
import dev.axonia.communication.api.MailCredentials;
import dev.axonia.communication.api.MailOutcome;
import dev.axonia.communication.api.MailRefusal;
import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerSettings;
import dev.axonia.communication.api.TestMail;
import dev.axonia.communication.application.port.out.TransportFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class SmtpTestMailSenderTest {

    private static final TestMail MAIL = new TestMail("owner@example.com", "Test mail from axonia",
            "Alice Owner sent this test mail on 4 Oct 2026 at 14:02 UTC.");

    private static final SmtpTimeouts QUICK = new SmtpTimeouts(Duration.ofSeconds(1), Duration.ofMillis(300),
            Duration.ofMillis(300));

    @Test
    void sendsOneMessageThroughARealServerAndAnswersAccepted() {

        // given
        var mailpit = Mailpit.open();
        mailpit.clear();
        var candidate = MailServerSettings.builder().host(mailpit.host()).port(mailpit.smtpPort())
                .security(MailSecurity.NONE).credentials(Optional.of(new MailCredentials("axonia", "any")))
                .fromAddress("axonia@acme.test").fromName(Optional.of("Acme Communities")).build();

        // when
        var actual = new SmtpTestMailSender(new JavaMailTransports()).send(candidate, MAIL);

        // then
        assertThat(actual).isEqualTo(MailOutcome.accepted());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(mailpit.messages()).containsExactly(new Mailpit.Message("axonia@acme.test",
                        "Acme Communities", List.of("owner@example.com"), "Test mail from axonia")));
    }

    @Test
    void answersConnectionFailedWhenNothingListens() throws IOException {

        // given
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // when
        var actual = new SmtpTestMailSender(new JavaMailTransports(), QUICK, Duration.ofSeconds(5)).send(on(port),
                MAIL);

        // then
        assertThat(actual).isEqualTo(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    /** The operating system completes the handshake from the backlog; nothing ever writes the greeting. */
    @Test
    void answersTimeoutWhenTheServerNeverGreets() throws IOException {

        try (var silent = new ServerSocket(0)) {

            // when
            var actual = new SmtpTestMailSender(new JavaMailTransports(), QUICK, Duration.ofSeconds(5))
                    .send(on(silent.getLocalPort()), MAIL);

            // then
            assertThat(actual).isEqualTo(MailOutcome.refused(MailRefusal.TIMEOUT));
        }
    }

    /**
     * The attempt it gives up on is stopped, not left behind: its connection is closed within a second, where the
     * socket timeouts alone would hold it open for five.
     */
    @Test
    void stopsAtItsCeilingWhateverTheSocketTimeoutsAllow() throws IOException {

        try (var silent = new ServerSocket(0)) {
            silent.setSoTimeout(1_000);
            var started = System.nanoTime();

            // when
            var actual = new SmtpTestMailSender(new JavaMailTransports(), MailSenders.DELIVERY, Duration.ofMillis(300))
                    .send(on(silent.getLocalPort()), MAIL);

            // then
            assertThat(actual).isEqualTo(MailOutcome.refused(MailRefusal.TIMEOUT));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
            try (var accepted = silent.accept()) {
                accepted.setSoTimeout(1_000);
                assertThat(accepted.getInputStream().read()).isEqualTo(-1);
            }
        }
    }

    @Test
    void answersTimeoutAndKeepsTheInterruptWhenItsCallerIsInterrupted() throws IOException {

        try (var silent = new ServerSocket(0)) {
            Thread.currentThread().interrupt();

            // when
            var actual = new SmtpTestMailSender(new JavaMailTransports()).send(on(silent.getLocalPort()), MAIL);

            // then
            assertThat(Thread.interrupted()).isTrue();
            assertThat(actual).isEqualTo(MailOutcome.refused(MailRefusal.TIMEOUT));
        }
    }

    /** A test passes only for a server delivery can use, so it waits on each step no longer than delivery does. */
    @Test
    void connectsWithTheTimeoutsDeliveryUses() {

        // given
        var connectedWith = new AtomicReference<Properties>();
        TransportFactory recording = sender -> {
            connectedWith.set(sender.getJavaMailProperties());
            return new ScriptedTransport(null, null);
        };

        // when
        var actual = new SmtpTestMailSender(recording).send(on(25), MAIL);

        // then
        assertThat(actual).isEqualTo(MailOutcome.accepted());
        assertThat(connectedWith.get()).containsAllEntriesOf(MailSenders.DELIVERY.asProperties());
    }

    @Test
    void answersRecipientRefusedWithTheServersReplyLine() throws Exception {

        // given
        var refusal = new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress("owner@example.com"), "RCPT TO:<owner@example.com>",
                        550, "550 5.1.0 Requested action not taken: mailbox unavailable\n"));

        // when
        var actual = new SmtpTestMailSender(sender -> new ScriptedTransport(refusal, null)).send(on(25), MAIL);

        // then
        assertThat(actual).isEqualTo(MailOutcome.refused(MailRefusal.RECIPIENT_REFUSED,
                Optional.of("550 5.1.0 Requested action not taken: mailbox unavailable")));
    }

    @Test
    void sendsThePlainTextTheCallerWroteFromTheCandidatesAddress() throws Exception {

        // given
        var transport = new ScriptedTransport(null, null);

        // when
        new SmtpTestMailSender(sender -> transport).send(on(25), MAIL);

        // then
        assertThat(transport.sent.getSubject()).isEqualTo("Test mail from axonia");
        assertThat(transport.sent.getContent()).isEqualTo(MAIL.text());
        // the transport writes the headers on the wire the way this does; until then the type reads text/plain anyway
        transport.sent.saveChanges();
        assertThat(transport.sent.getContentType()).startsWith("text/plain");
        assertThat(transport.sent.getFrom()[0].toString()).isEqualTo("axonia@localhost");
        assertThat(transport.sent.getAllRecipients()[0].toString()).isEqualTo("owner@example.com");
    }

    @Test
    void aTransportThatFailsToCloseStillAnswersAccepted() {

        // when
        var actual = new SmtpTestMailSender(
                sender -> new ScriptedTransport(null, new MessagingException("the socket was already gone")))
                .send(on(25), MAIL);

        // then
        assertThat(actual).isEqualTo(MailOutcome.accepted());
    }

    @Test
    void aFailureOutsideTheMailConversationIsNotAnOutcome() {

        // given
        TransportFactory broken = sender -> {
            throw new IllegalStateException("no transport provider");
        };

        // when / then
        assertThatThrownBy(() -> new SmtpTestMailSender(broken).send(on(25), MAIL))
                .isInstanceOf(IllegalStateException.class).hasRootCauseMessage("no transport provider");
    }

    private static MailServerSettings on(int port) {

        return MailServerSettings.builder().host("127.0.0.1").port(port).security(MailSecurity.NONE)
                .fromAddress("axonia@localhost").build();
    }

    /** Already connected; refuses the message, or fails to close, when told to. */
    private static final class ScriptedTransport extends Transport {

        private final MessagingException refusal;
        private final MessagingException closeFailure;
        private MimeMessage sent;

        private ScriptedTransport(MessagingException refusal, MessagingException closeFailure) {
            super(Session.getInstance(new Properties()), null);
            this.refusal = refusal;
            this.closeFailure = closeFailure;
            setConnected(true);
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) throws MessagingException {

            if (refusal != null) {
                throw refusal;
            }
            sent = (MimeMessage) message;
        }

        @Override
        public void close() throws MessagingException {

            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
