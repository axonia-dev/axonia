package dev.axonia.communication.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import dev.axonia.IntegrationMetrics;
import dev.axonia.UsageCounters;
import dev.axonia.communication.DeliveryOutcome;
import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailRefusal;
import dev.axonia.communication.MailSecurity;
import dev.axonia.communication.MailServerSettings;
import dev.axonia.communication.MailServerState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter's own decisions: opening one connection per batch, assembling the multipart message, setting the
 * one-click unsubscribe headers only when a link is present, and sanitizing the subject before it becomes a header. A
 * fake transport rather than a real SMTP server, following {@code SmtpReportDispatcherTest}: the message this builds is
 * what {@code MimeMessageHelper} converts, so standing up a server would be asserting Spring's and Jakarta Mail's
 * conversion, not ours.
 *
 * <p>
 * The sender is the real one {@link MailSenders} builds from the settings {@link RecordingMailServer} answers, because
 * the adapter asks it for nothing but {@code createMimeMessage}, which needs no SMTP host. Everything that leaves the
 * process goes through {@link TransportFactory} instead, which is what the fakes below stand in for.
 */
class SmtpDeliveryTest {

    private final RecordingMailServer mailServer = RecordingMailServer.configured(RecordingMailServer.LOCAL);
    private final RecordingTransport transport = new RecordingTransport();
    private final CountingTransports transports = new CountingTransports(transport);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationMetrics metrics = new IntegrationMetrics(registry);
    private final UsageCounters usageCounters = new UsageCounters(registry);
    private final SmtpDelivery delivery = new SmtpDelivery(mailServer, new MailSenders(), transports, metrics,
            usageCounters);

    @Test
    void sendsBothAnHtmlAndAPlainTextPart() throws Exception {

        send(new OutgoingMail("member@example.com", "Weekly digest", "<p>Hi there</p>", "Hi there", null));

        assertThat(rawMessageText()).contains("multipart/alternative").contains("Hi there").contains("<p>Hi there</p>");
    }

    @Test
    void setsFromToTheConfiguredAddress() throws Exception {

        send(new OutgoingMail("member@example.com", "Weekly digest", "<p>Hi</p>", "Hi", null));

        assertThat(transport.sent.getFrom()[0].toString()).isEqualTo("axonia@localhost");
    }

    @Test
    void setsTheFromNameWhenTheSettingsCarryOne() throws Exception {

        mailServer.answer(MailServerState
                .configured(MailServerSettings.builder().host("localhost").port(1025).security(MailSecurity.NONE)
                        .fromAddress("axonia@localhost").fromName(Optional.of("Acme Communities")).build()));

        send(aMail());

        var from = (InternetAddress) transport.sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("axonia@localhost");
        assertThat(from.getPersonal()).isEqualTo("Acme Communities");
    }

    /** Nobody to call, so nothing is counted against the provider and no connection is asked for. */
    @Test
    void refusesToOpenWhileNoMailServerIsConfigured() {

        mailServer.answer(MailServerState.notConfigured());

        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class)
                .hasMessage("the mail server is not configured");
        assertThat(transports.senders).isEmpty();
        assertThat(calls("success")).isZero();
        assertThat(calls("failure")).isZero();
    }

    @Test
    void connectsWithASenderBuiltFromTheSettingsTheServerAnswers() {

        send(aMail());

        assertThat(transports.senders).singleElement().satisfies(sender -> {
            assertThat(sender.getHost()).isEqualTo("localhost");
            assertThat(sender.getPort()).isEqualTo(1025);
        });
    }

    @Test
    void reusesOneSenderWhileTheSettingsStayTheSame() {

        send(aMail());
        send(aMail());

        assertThat(transports.senders).hasSize(2);
        assertThat(transports.senders.get(1)).isSameAs(transports.senders.get(0));
    }

    /** An Owner's save reaches the next connection opened, never one already open. */
    @Test
    void buildsANewSenderWhenTheSettingsChange() {

        send(aMail());
        mailServer.answer(MailServerState.configured(MailServerSettings.builder().host("localhost").port(2525)
                .security(MailSecurity.NONE).fromAddress("axonia@localhost").build()));
        send(aMail());

        assertThat(transports.senders.get(1)).isNotSameAs(transports.senders.get(0));
        assertThat(transports.senders.get(1).getPort()).isEqualTo(2525);
    }

    @Test
    void setsTheOneClickUnsubscribeHeadersWhenALinkIsPresent() throws Exception {

        send(new OutgoingMail("member@example.com", "Weekly digest", "<p>Hi</p>", "Hi",
                "https://axonia.example/public/notifications/unsubscribe?token=abc"));

        assertThat(transport.sent.getHeader("List-Unsubscribe", null))
                .isEqualTo("<https://axonia.example/public/notifications/unsubscribe?token=abc>");
        assertThat(transport.sent.getHeader("List-Unsubscribe-Post", null)).isEqualTo("List-Unsubscribe=One-Click");
    }

    @Test
    void omitsTheUnsubscribeHeadersWhenNoLinkIsPresent() throws Exception {

        send(new OutgoingMail("member@example.com", "Weekly digest", "<p>Hi</p>", "Hi", null));

        assertThat(transport.sent.getHeader("List-Unsubscribe")).isNull();
        assertThat(transport.sent.getHeader("List-Unsubscribe-Post")).isNull();
    }

    /**
     * The whole point of the batch: connect, STARTTLS and AUTH are paid once, however many messages leave through it.
     * Counting connections rather than verifying {@code Transport#connect} is deliberate - {@link TransportFactory}
     * hands back a transport that is ALREADY connected, so "how many times did we connect" is "how many times did we
     * ask the factory", and nothing else in this class can observe it.
     */
    @Test
    void oneBatchConnectsOnceHoweverManyMessagesItSends() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
            batch.send(aMail());
            batch.send(aMail());
        }

        assertThat(transports.senders).hasSize(1);
        assertThat(transport.sends).isEqualTo(3);
        assertThat(transport.closes).isEqualTo(1);
    }

    /**
     * The companion to {@link #oneBatchConnectsOnceHoweverManyMessagesItSends}: two batches are two connections, so the
     * connection genuinely belongs to the batch rather than being cached for the life of the adapter. A cached one
     * would look identical to the test above and be wrong in production, where the provider closes an idle connection
     * between two ticks and nothing here would ever notice.
     */
    @Test
    void aSecondBatchOpensASecondConnection() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
        }
        try (var batch = delivery.open()) {
            batch.send(aMail());
        }

        assertThat(transports.senders).hasSize(2);
        assertThat(transport.closes).isEqualTo(2);
    }

    /**
     * A batch that sent correctly must not fail because the socket did not shut down cleanly: every message has already
     * left, and the only consequence is one connection the provider reaps on its own.
     */
    @Test
    void aFailureClosingTheTransportNeverFailsABatchThatSentCorrectly() {

        transport.failCloseWith(new MessagingException("the socket was already gone"));

        assertThatCode(() -> send(aMail())).doesNotThrowAnyException();

        assertThat(transport.sends).isEqualTo(1);
    }

    /**
     * The trap this class exists to close: nothing consumed {@code MailRenderer}'s subject before this class, so a raw
     * CR/LF surviving into a session title was never exercised as a header value until now. An embedded
     * {@code \r\nBcc: evil@example.com} must never become a second header. Rejecting the whole send, rather than
     * silently stripping the control characters and sending a slightly mangled subject nobody would ever notice, is the
     * chosen response - see the class javadoc for why.
     */
    @Test
    void rejectsAControlCharacterInTheSubjectRatherThanSplicingASecondHeader() {

        var mail = new OutgoingMail("member@example.com", "Weekly digest\r\nBcc: evil@example.com", "<p>Hi</p>", "Hi",
                null);

        assertThatThrownBy(() -> send(mail)).isInstanceOf(MailSendException.class)
                .hasMessageNotContaining("evil@example.com").hasMessageNotContaining("Bcc");
        assertThat(transport.sent).isNull();
    }

    /**
     * {@code unsubscribeUrl} becomes a header value the same way {@code subject} does, so it is rejected on the same
     * principle even though it is expected to be this module's own signed link rather than free text.
     */
    @Test
    void rejectsAControlCharacterInTheUnsubscribeUrlToo() {

        var mail = new OutgoingMail("member@example.com", "Weekly digest", "<p>Hi</p>", "Hi",
                "https://axonia.example/u/abc\r\nBcc: evil@example.com");

        assertThatThrownBy(() -> send(mail)).isInstanceOf(MailSendException.class)
                .hasMessageNotContaining("evil@example.com").hasMessageNotContaining("Bcc");
        assertThat(transport.sent).isNull();
    }

    /**
     * A failure assembling the message must never let the address or the body leak into the exception's message. The
     * address is deliberately non-empty and malformed rather than blank, so the "never quotes the address" half of this
     * test asserts against a real address that could have leaked, not an absent one.
     */
    @Test
    void aMessagingFailureDuringAssemblyNeverQuotesTheAddressOrTheBody() {

        var mail = new OutgoingMail("a b c@example.com", "Weekly digest", "<p>a very secret html body</p>",
                "a very secret text body", null);

        assertThatThrownBy(() -> send(mail)).isInstanceOf(MailSendException.class)
                .hasMessageNotContaining("a b c@example.com").hasMessageNotContaining("a very secret html body")
                .hasMessageNotContaining("a very secret text body");
    }

    /**
     * The checked {@link MessagingException} the transport raises never escapes: {@link MailBatch#send} declares
     * nothing, so the outbox's per-row handling can stay a plain {@code catch (RuntimeException)}. The message this
     * class writes in its place names no reply text at all - a rejected reply routinely echoes the recipient straight
     * back - so the provider's own words survive only as the cause, which no log line here ever prints.
     */
    @Test
    void aTransportFailureBecomesAnUncheckedMailSendExceptionCarryingTheReplyAsItsCause() {

        var refusal = new MessagingException("550 5.1.1 <member@example.com>: Recipient address rejected");
        transport.refuseNextWith(refusal);

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class)
                .hasMessageNotContaining("member@example.com").hasCause(refusal);
    }

    /**
     * A connection that dies part way through a batch is a property of the batch, not of the row that happened to be in
     * flight: the provider dropping an idle socket or closing on a messages-per-connection cap answers with a reply
     * naming no limit, and every remaining row would answer identically. Left an ordinary per-row failure, one blip
     * would burn an attempt on every remaining row toward {@code maxAttempts} for messages nothing ever refused - where
     * the pre-batch code, one connection per message, burned exactly one. Raised as an account refusal instead,
     * {@link OutboxDrain} pauses the tick and releases the rest of the batch unmarked.
     */
    @Test
    void aConnectionThatDiesMidBatchIsAnAccountRefusalRatherThanAPerRowFailure() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
            transport.dropConnectionAndRefuseWith(new MessagingException("Can't send command to SMTP host"));

            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailAccountRefusedException.class);
        }

        assertThat(transport.sends).isEqualTo(1);
    }

    /**
     * A quota rejection is about the account, not this one message: every row behind it would be refused identically,
     * so {@link OutboxDrain} needs to tell it apart from an ordinary send failure. Only the reply text is inspected,
     * never the address or the body, per this class's own rule.
     */
    @Test
    void aQuotaRejectionIsRaisedAsAnAccountRefusalRatherThanAnOrdinaryFailure() {

        transport.refuseNextWith(new MessagingException("550 5.7.1 Daily quota exceeded"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * The companion to the quota case above: a reply that names neither a quota, a rate limit, nor "too many messages"
     * stays an ordinary, per-row failure rather than pausing the whole tick.
     */
    @Test
    void anOrdinaryTransportFailureStaysAnOrdinaryFailure() {

        transport.refuseNextWith(new MessagingException("451 4.3.0 Temporary local problem"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * The other shape a transport uses to refuse an account outright: a rejected credential, which JavaMail raises when
     * the connection is established rather than when a message is sent. It never reaches the reply-text sniffing
     * {@link SmtpDelivery} does for the quota case, because no message has been assembled yet.
     */
    @Test
    void aRejectedCredentialIsRaisedAsAnAccountRefusalToo() {

        transports.refuseWith(new AuthenticationFailedException("bad credentials"));

        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * The deliberate widening the batch brings with it: a host that simply cannot be reached says nothing about any one
     * message either, so it now aborts the tick and releases every claimed row unmarked, where the pre-batch code
     * burned an attempt on each row in turn and eventually abandoned them.
     */
    @Test
    void anUnreachableHostIsAnAccountRefusalRatherThanAPerRowFailure() {

        transports.refuseWith(new MessagingException("Could not connect to SMTP host"));

        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * Providers do not agree on wording: SES answers a daily cap with "Throttling failure", naming neither
     * {@code quota} nor {@code rate limit} - the two words the classifier started with. Missing this reply would let
     * the ABANDONED-by-attrition bug the account-refusal path exists to close resurface behind a provider using
     * different words for it.
     */
    @Test
    void aProviderThrottlingReplyIsRaisedAsAnAccountRefusalToo() {

        transport.refuseNextWith(new MessagingException("454 4.3.0 Throttling failure: Maximum sending rate exceeded"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * The false-positive half of the same fix: a rejected reply routinely echoes the recipient back in angle brackets,
     * and this address is deliberately chosen to contain the word "quota" itself. Classifying on the raw reply text
     * would misfire on the address rather than the account, and - since this path deliberately never burns an attempt -
     * would then block this one poison row's organization queue on every following tick.
     */
    @Test
    void anAddressEmbeddedInTheReplyNeverDrivesTheClassification() {

        transport.refuseNextWith(new MessagingException("550 5.1.1 <quota@example.com>: Recipient address rejected"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * The same false-positive, narrowed to a malformed bounce: a truncated or otherwise broken reply can leave a
     * {@code <} with no matching {@code >}. A strip that only recognised a well-formed pair would leave this address
     * text unstripped and still scanned, letting it drive the classification the same way an ordinary well-formed one
     * would - the exact failure mode {@link #anAddressEmbeddedInTheReplyNeverDrivesTheClassification} closes, just
     * reachable through a different input.
     */
    @Test
    void anUnclosedAngleBracketStillNeverDrivesTheClassification() {

        transport.refuseNextWith(new MessagingException("501 <quota@sub.example: Bad sender address syntax"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * A real reply often arrives nested rather than bare: Jakarta Mail reports the provider's own answer as a chained
     * exception under a flat outer one, and {@code MessagingException#getMessage} does NOT fold the nested text into
     * its own. Classifying on the outer message alone - which is all the pre-batch code, reading Spring's own
     * {@code failedMessages} wrapper, ever needed to do - would miss every wrapped quota reply and let the exact
     * {@code ABANDONED}-by-attrition bug this classification exists to close come straight back.
     */
    @Test
    void aQuotaReplyNestedUnderAnOuterFailureIsStillRaisedAsAnAccountRefusal() {

        transport.refuseNextWith(
                new MessagingException("Sending failed", new MessagingException("550 5.7.1 Daily quota exceeded")));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * The two halves of the classification meeting: an unclosed {@code <} is stripped to end-of-string, so stripping
     * the joined chain in one pass would let a malformed address in the OUTER message swallow the nested reply behind
     * it and turn a genuine account refusal back into an ordinary per-row failure - silently, and only for the
     * malformed bounces nobody tests against.
     */
    @Test
    void anUnclosedAngleBracketInAnOuterFailureNeverHidesTheNestedQuotaReply() {

        transport.refuseNextWith(new MessagingException("501 <malformed@sub.example",
                new MessagingException("550 5.7.1 Daily quota exceeded")));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailAccountRefusedException.class);
    }

    /**
     * Two things leave this process for the provider - the connection, and each message over it - and both are counted,
     * so one batch of two messages is three calls. Counting only the sends would leave a rejected credential invisible:
     * it never reaches a send at all.
     */
    @Test
    void countsTheConnectionAndEveryMessageAsACallToTheProvider() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
            batch.send(aMail());
        }

        assertThat(calls("success")).isEqualTo(3.0);
    }

    /** The whole reason the counter exists: a provider that starts refusing shows up on a graph. */
    @Test
    void countsARejectedCredentialAsAFailedCall() {

        transports.refuseWith(new AuthenticationFailedException("bad credentials"));

        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class);

        assertThat(calls("failure")).isEqualTo(1.0);
    }

    @Test
    void countsARefusedMessageAsAFailedCall() {

        transport.refuseNextWith(new MessagingException("451 4.3.0 Temporary local problem"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class);

        assertThat(calls("failure")).isEqualTo(1.0);
    }

    /**
     * Assembly sits outside the counted region on purpose: a subject refused for a control character is this
     * application's own decision about its own data, and counting it against the provider would put axonia's bugs on
     * the provider's graph. Only the connection, which did happen, is counted here.
     */
    @Test
    void neverCountsAMessageRefusedBeforeItReachedTheProvider() {

        var mail = new OutgoingMail("member@example.com", "Weekly digest\r\nBcc: evil@example.com", "<p>Hi</p>", "Hi",
                null);

        assertThatThrownBy(() -> send(mail)).isInstanceOf(MailSendException.class);

        assertThat(calls("failure")).isZero();
        assertThat(calls("success")).isEqualTo(1.0);
    }

    /**
     * The product counter, and the half of the overlap with the integration counter that makes both worth keeping: the
     * same batch is three calls to the provider and two messages accepted for delivery.
     */
    @Test
    void countsEveryDeliveredMessageAsProductUsage() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
            batch.send(aMail());
        }

        assertThat(emailsSent()).isEqualTo(2.0);
        assertThat(calls("success")).isEqualTo(3.0);
    }

    /** The transport refused it, so nothing is counted: a refusal must not read as a message that went out. */
    @Test
    void neverCountsARefusedMessageAsProductUsage() {

        transport.refuseNextWith(new MessagingException("451 4.3.0 Temporary local problem"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class);

        assertThat(emailsSent()).isZero();
    }

    @Test
    void reportsNothingUntilAskedAndThenThatTheBatchWasAccepted() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
            batch.send(aMail());
        }
        assertThat(mailServer.outcomes()).isEmpty();

        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.accepted());
    }

    @Test
    void reportsEachOutcomeOnce() {

        send(aMail());

        delivery.reportOutcome();
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.accepted());
    }

    /** No recipient is named before the connection is up, so the reply line is safe to keep. */
    @Test
    void reportsARefusedConnectionWithItsReasonAndReplyLine() {

        transports.refuseWith(new AuthenticationFailedException("535 5.7.8 Authentication credentials invalid\n"));

        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class);
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED,
                Optional.of("535 5.7.8 Authentication credentials invalid")));
        assertThat(mailServer.reports()).singleElement()
                .satisfies(report -> assertThat(report.isAbout(RecordingMailServer.LOCAL)).isTrue());
    }

    /** A save landing while a batch is open changes nothing it connected with, so its outcome names the old values. */
    @Test
    void eachOutcomeNamesTheSettingsItsBatchConnectedWith() {

        // given
        var saved = MailServerSettings.builder().host("localhost").port(2525).security(MailSecurity.NONE)
                .fromAddress("axonia@localhost").build();
        try (var batch = delivery.open()) {
            mailServer.answer(MailServerState.configured(saved));
            batch.send(aMail());
        }

        // when
        delivery.reportOutcome();

        // then
        assertThat(mailServer.reports())
                .containsExactly(DeliveryOutcome.of(RecordingMailServer.LOCAL, MailOutcome.accepted()));
    }

    @Test
    void aRefusalOutranksAnAcceptanceInTheSameBatch() {

        try (var batch = delivery.open()) {
            batch.send(aMail());
            transport.dropConnectionAndRefuseWith(new MessagingException("Can't send command to SMTP host"));
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailAccountRefusedException.class);
        }
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    /**
     * Workers of one batch race on the outcome: one connection refused while the others go on delivering must still
     * read refused, or the provider's cap on concurrent connections never reaches the card.
     */
    @Test
    void anAcceptanceAfterARefusalLeavesTheRefusalToBeReported() {

        transports.refuseWith(new AuthenticationFailedException("535 5.7.8 Authentication credentials invalid\n"));
        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class);
        transports.acceptAgain();

        send(aMail());
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED,
                Optional.of("535 5.7.8 Authentication credentials invalid")));
    }

    /** The reply on a dropped connection can name a recipient, so only its reason travels. */
    @Test
    void aDroppedConnectionIsReportedWithoutItsReply() {

        try (var batch = delivery.open()) {
            transport.dropConnectionAndRefuseWith(
                    new MessagingException("421 4.7.0 <member@example.com> too many messages, closing"));
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailAccountRefusedException.class);
        }
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    /** Every row behind it would be refused the same way, so the queue waits for the fix instead of abandoning. */
    @Test
    void aRefusedSenderHoldsTheQueueAndIsReportedWithItsReply() {

        transport.refuseNextWith(new SMTPSendFailedException("MAIL FROM:<axonia@localhost>", 550,
                "550 5.7.1 Sender address rejected: not owned by user\n", null, null, null, null));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailAccountRefusedException.class);
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.SENDER_REFUSED,
                Optional.of("550 5.7.1 Sender address rejected: not owned by user")));
    }

    /**
     * An unverified sending identity, answered after DATA to every message: the server refuses everything, so it hears
     * of it, by its reason alone since such a reply can list a recipient. No row is held: a content filter answers one
     * message the same way.
     */
    @Test
    void aSenderRefusedAfterDataForEveryMessageOfTheBatchIsReported() {

        // given
        transport.refuseNextWith(unverifiedSendingIdentity());

        // when
        try (var batch = delivery.open()) {
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailSendException.class)
                    .isNotInstanceOf(MailAccountRefusedException.class);
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailSendException.class)
                    .isNotInstanceOf(MailAccountRefusedException.class);
        }
        delivery.reportOutcome();

        // then
        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.SENDER_REFUSED));
    }

    /** Refused first and accepted after, so neither a refusal set over the acceptance nor one set before it passes. */
    @Test
    void aSenderRefusedAfterDataBesideAnAcceptedMessageReportsTheAcceptance() {

        // given
        transport.refuseNextWith(unverifiedSendingIdentity());

        // when
        try (var batch = delivery.open()) {
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailSendException.class);
            transport.acceptAgain();
            batch.send(aMail());
        }
        delivery.reportOutcome();

        // then
        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.accepted());
    }

    @Test
    void aRefusalOfTheConnectionOutranksASenderRefusedAfterData() {

        // given
        transport.refuseNextWith(unverifiedSendingIdentity());

        // when
        try (var batch = delivery.open()) {
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailSendException.class);
            transport.dropConnectionAndRefuseWith(new MessagingException("Can't send command to SMTP host"));
            assertThatThrownBy(() -> batch.send(aMail())).isInstanceOf(MailAccountRefusedException.class);
        }
        delivery.reportOutcome();
        delivery.reportOutcome();

        // then
        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    @Test
    void aRefusedRecipientStaysOneRowsFailureAndIsNotReported() throws Exception {

        transport.refuseNextWith(new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress("member@example.com"),
                        "RCPT TO:<member@example.com>", 550,
                        "550 5.1.1 <member@example.com>: Recipient address rejected")));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailAccountRefusedException.class);
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).isEmpty();
    }

    /** A recipient domain's TLS policy, answered after DATA, is that row's alone and must not pause the tick. */
    @Test
    void aTlsPolicyReplyForOneRecipientStaysThatRowsFailureAndDoesNotHoldTheQueue() {

        transport.refuseNextWith(new SMTPSendFailedException(".", 550,
                "550 5.7.1 TLS is required by the recipient domain\n", null, null, null, null));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailAccountRefusedException.class);
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).isEmpty();
    }

    /** A quota reply can echo the recipient, so only its reason travels. */
    @Test
    void anAccountLimitIsReportedAsARefusedSenderWithoutItsReply() {

        transport.refuseNextWith(new MessagingException("550 5.7.1 Daily quota exceeded for <member@example.com>"));

        assertThatThrownBy(() -> send(aMail())).isInstanceOf(MailAccountRefusedException.class);
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).containsExactly(MailOutcome.refused(MailRefusal.SENDER_REFUSED));
    }

    @Test
    void aMailServerThatIsNotConfiguredIsNotReportedAsARefusal() {

        mailServer.answer(MailServerState.notConfigured());

        assertThatThrownBy(delivery::open).isInstanceOf(MailAccountRefusedException.class);
        delivery.reportOutcome();

        assertThat(mailServer.outcomes()).isEmpty();
    }

    @Test
    void aFailureRecordingTheOutcomeNeverReachesTheDrain() {

        mailServer.failRecordingWith(new IllegalStateException("the settings row is locked"));
        send(aMail());

        assertThatCode(delivery::reportOutcome).doesNotThrowAnyException();
    }

    private double emailsSent() {

        return registry.get("axonia.usage.events").tag("event", "email-sent").counter().count();
    }

    private double calls(String outcome) {

        return registry.get("axonia.integration.calls").tag("integration", "smtp").tag("outcome", outcome).counter()
                .count();
    }

    private void send(OutgoingMail mail) {

        try (var batch = delivery.open()) {
            batch.send(mail);
        }
    }

    /** Amazon SES once the sending identity's verification has lapsed. */
    private static SMTPSendFailedException unverifiedSendingIdentity() {

        return new SMTPSendFailedException(".", 554,
                "554 Message rejected: Email address is not verified. The following identities failed the check in"
                        + " region US-EAST-1: axonia@localhost\n",
                null, null, null, null);
    }

    private static OutgoingMail aMail() {

        return new OutgoingMail("member@example.com", "Weekly digest", "<p>Hi</p>", "Hi", null);
    }

    private String rawMessageText() throws Exception {

        var raw = new ByteArrayOutputStream();
        transport.sent.writeTo(raw);
        return raw.toString(StandardCharsets.UTF_8);
    }

    /** Records the sender each connection was asked for; the transport it hands back is already connected. */
    private static final class CountingTransports implements TransportFactory {

        private final Transport transport;
        private final List<JavaMailSenderImpl> senders = new ArrayList<>();
        private MessagingException refusal;

        private CountingTransports(Transport transport) {
            this.transport = transport;
        }

        void refuseWith(MessagingException refusal) {

            this.refusal = refusal;
        }

        void acceptAgain() {

            refusal = null;
        }

        @Override
        public Transport connected(JavaMailSenderImpl sender) throws MessagingException {

            if (refusal != null) {
                throw refusal;
            }
            senders.add(sender);
            return transport;
        }
    }

    /**
     * Extends the real {@link Transport} and overrides only the two methods the batch calls, so the fake cannot drift
     * from the interface as Jakarta Mail grows. It speaks to no socket: {@code sendMessage} is where every SMTP
     * conversation this class cares about would happen, and it is stubbed out entirely. It does carry the real
     * connected flag through {@code Service#setConnected}, rather than overriding {@code isConnected}, so the adapter's
     * "is this connection still there" check runs against Jakarta Mail's own implementation.
     */
    private static final class RecordingTransport extends Transport {

        private MimeMessage sent;
        private int sends;
        private int closes;
        private MessagingException refusal;
        private MessagingException closeFailure;

        private RecordingTransport() {
            super(Session.getInstance(new Properties()), null);
            setConnected(true);
        }

        void refuseNextWith(MessagingException refusal) {

            this.refusal = refusal;
        }

        void acceptAgain() {

            refusal = null;
        }

        void dropConnectionAndRefuseWith(MessagingException refusal) {

            this.refusal = refusal;
            setConnected(false);
        }

        void failCloseWith(MessagingException closeFailure) {

            this.closeFailure = closeFailure;
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) throws MessagingException {

            if (refusal != null) {
                throw refusal;
            }
            sends++;
            sent = (MimeMessage) message;
        }

        @Override
        public void close() throws MessagingException {

            closes++;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
