package dev.axonia.communication.internal;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import dev.axonia.IntegrationMetrics;
import dev.axonia.IntegrationMetrics.Integration;
import dev.axonia.UsageCounters;
import dev.axonia.communication.DeliveryOutcome;
import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailRefusal;
import dev.axonia.communication.MailServer;
import dev.axonia.communication.MailServerSettings;
import dev.axonia.communication.MailServerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends over the SMTP transport. Builds a multipart/alternative message through {@code MimeMessageHelper} - the
 * plain-text part for a client that does not render HTML, the HTML part for one that does - and sets the one-click
 * unsubscribe headers when {@link OutgoingMail} carries a link, leaving them off the message entirely otherwise.
 *
 * <p>
 * Every message leaves through a {@link MailBatch} rather than through {@code JavaMailSender#send}, which connects,
 * negotiates STARTTLS, authenticates and disconnects once per message: against a remote provider that handshake is the
 * dominant cost of sending one. {@link #open} pays it once and {@link SmtpBatch} then sends over the connection that is
 * already up - see {@link MailBatch}'s own javadoc for why the batch is a sequence of single sends rather than one
 * {@code send(MimeMessage[])}.
 *
 * <p>
 * {@code subject} and {@code unsubscribeUrl} are both checked before becoming a header, and the send is refused, whole,
 * if either carries a control character. Nothing consumed {@link RenderedMail#subject} before this class -
 * {@code MailRenderer} only strips the ends - so a raw CR or LF surviving into a {@code sessionTitle} would otherwise
 * become the first header injection this codebase has ever handed an SMTP library, splicing an arbitrary extra header
 * (a {@code Bcc}, for instance) into the message. {@code unsubscribeUrl} becomes a header value the same way and is
 * checked on the same principle, even though it is expected to be this module's own signed link rather than free text.
 * Rejecting rather than silently stripping is deliberate: a stripped subject still sends, quietly missing a stray
 * character forever, which is a worse outcome for a legitimate title that happens to contain one than a refused send
 * that retries and eventually surfaces as {@code FAILED} - a diagnosable, visible state, not a slightly wrong one
 * nobody notices.
 *
 * <p>
 * Two things are counted against the {@code smtp} integration: opening the connection, and sending one message over it.
 * Both are round trips to the provider, and both are ways it refuses - a rejected credential or an account over its cap
 * never reaches a send at all, so counting only sends would leave the refusal this metric exists for invisible.
 * Assembling the message is deliberately outside the counted region: it talks to nobody, and a subject refused for a
 * control character is this application's own decision, not the provider's.
 *
 * <p>
 * A successful send is additionally counted as {@link UsageCounters.Event#EMAIL_SENT}. That overlaps the {@code smtp}
 * integration counter on purpose and neither is a duplicate of the other: {@code
 * axonia.integration.calls} counts round trips to the provider, one of which is the connection each batch opens before
 * any message rides over it, while {@code axonia.usage.events} counts messages the transport accepted for delivery.
 * Deleting either would silently change what the other's panel means. Neither is a delivery receipt: what happens to an
 * accepted message afterwards - a bounce, a spam folder, a silently discarded recipient - is not reported back on this
 * connection and is not counted anywhere.
 *
 * <p>
 * No log line and no exception message here ever quotes a recipient address or any part of a body. A failure assembling
 * the message becomes an unchecked {@link MailSendException} carrying only the underlying cause, never the address or
 * the message text; a transport-level failure is rewrapped the same way, carrying only the reply the provider sent
 * back.
 *
 * <p>
 * {@link #reportOutcome} hands the mail server the last batch's outcome on the drain's own thread, naming the settings
 * the batch connected with; a refusal of the connection or of the sending account carries its reason and reply line, a
 * dropped connection or an account limit only its reason, since those replies can name a recipient.
 *
 * <p>
 * A sender refused after DATA (an unverified sending identity, a mailbox the account may not send as) is still that
 * message's failure, never a hold, since a content filter answers the same way for one message. It is reported, by its
 * reason alone for the same reason as an account limit, only when no message of the batch was accepted: then the server
 * is refusing everything, and one filtered message among accepted ones never turns the card amber.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SmtpDelivery implements MailDelivery {

    private static final String LIST_UNSUBSCRIBE = "List-Unsubscribe";
    private static final String LIST_UNSUBSCRIBE_POST = "List-Unsubscribe-Post";

    /**
     * Far past any real chain: this bound exists to stop a self-referencing cause spinning, not to truncate a reply.
     */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * Real providers do not agree on wording - SES answers a daily cap with "Throttling failure", Gmail with "Daily
     * user sending limit exceeded", SendGrid and Mailgun with "Too many ...". {@code quota} and {@code rate limit}
     * alone missed all four; this list is deliberately wider than three named cases so the same {@code
     * ABANDONED}-by-attrition bug the account-refusal path exists to close cannot resurface behind a provider using
     * different words for the same refusal.
     */
    private static final List<String> ACCOUNT_LIMIT_KEYWORDS = List.of("quota", "rate limit", "too many", "throttl",
            "sending limit");

    private final MailServer mailServer;
    private final MailSenders senders;
    private final TransportFactory transports;
    private final IntegrationMetrics metrics;
    private final UsageCounters usageCounters;
    private final AtomicReference<DeliveryOutcome> unreported = new AtomicReference<>();
    private final AtomicReference<DeliveryOutcome> senderRefusedAfterData = new AtomicReference<>();

    /**
     * Every way a connection can fail is an account refusal, whether the credential was rejected or the host was simply
     * unreachable: neither says anything about a message, and both would refuse every mail row behind this one
     * identically. Holding the mail unmarked is therefore right for both, where the pre-batch code burned an attempt on
     * each row in turn for the unreachable case and eventually abandoned it. A server that is not configured is refused
     * before anything is counted: there is nobody to call.
     */
    @Override
    public MailBatch open() {

        if (!(mailServer.current() instanceof MailServerState.Configured configured)) {
            throw new MailAccountRefusedException("the mail server is not configured", null);
        }
        var settings = configured.settings();
        var sender = senders.senderFor(settings);
        return metrics.call(Integration.SMTP, () -> {
            try {
                return new SmtpBatch(transports.connected(sender), sender, settings);
            } catch (MessagingException e) {
                unreported.set(DeliveryOutcome.of(settings, MailFailures.outcomeOf(e)));
                throw new MailAccountRefusedException("the mail transport refused the connection", e);
            }
        });
    }

    /**
     * A refusal replaces whatever was pending and an acceptance never replaces a refusal, so a batch that both sent and
     * was refused reads refused; the next batch that only sends clears it. A sender refused after DATA is reported only
     * when nothing else was: no message accepted and no other refusal.
     */
    @Override
    public void reportOutcome() {

        var pending = unreported.getAndSet(null);
        var senderRefused = senderRefusedAfterData.getAndSet(null);
        var outcome = pending == null ? senderRefused : pending;
        if (outcome == null) {
            return;
        }
        try {
            mailServer.recordOutcome(outcome);
        } catch (RuntimeException e) {
            log.warn("Recording how the mail server answered failed: {}", e.getClass().getSimpleName());
        }
    }

    private static MimeMessage assemble(OutgoingMail mail, JavaMailSenderImpl sender, MailServerSettings settings) {

        var message = sender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            MailSenders.setFrom(helper, settings);
            helper.setTo(mail.to());
            helper.setSubject(requireNoControlCharacters(mail.subject(), "subject"));
            helper.setText(mail.text(), mail.html());
            if (mail.unsubscribeUrl() != null) {
                message.setHeader(LIST_UNSUBSCRIBE,
                        "<" + requireNoControlCharacters(mail.unsubscribeUrl(), "unsubscribe URL") + ">");
                message.setHeader(LIST_UNSUBSCRIBE_POST, "List-Unsubscribe=One-Click");
            }
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailSendException("The message could not be assembled for sending", e);
        }
        return message;
    }

    /**
     * Never inspects the address or the body, only the reply text, per this class's rule that no log line or exception
     * message quotes either. A rejected reply routinely echoes the recipient straight back - {@code 550 5.1.1
     * <someone@example.com>: Recipient address rejected} - so anything inside {@code <...>} is stripped before the
     * keyword scan runs: without that, an address or domain that happens to contain one of these words would
     * misclassify an ordinary per-row failure as an account-wide one, and this path deliberately never burns an
     * attempt, so that one poison row would block its organization's queue on every following tick with nothing in the
     * log naming it. The strip also matches a {@code <} left unclosed by a truncated or malformed bounce - to
     * end-of-string rather than only to a paired {@code >} - since a recipient address must never drive this
     * classification regardless of how well-formed the reply happens to be. A quota, a throttling reply, or the
     * provider's own rate or sending limit all read as a 4xx or 5xx reply naming the limit rather than the recipient.
     */
    private static boolean isAccountLimit(MailSendException e) {

        var reply = replyText(e);
        return ACCOUNT_LIMIT_KEYWORDS.stream().anyMatch(reply::contains);
    }

    /**
     * The whole cause chain, not only the outermost message: Jakarta Mail routinely reports a provider's own reply as a
     * nested exception under a flat outer one ("Sending failed", "Invalid Addresses"), and
     * {@code MessagingException#getMessage} does not fold the nested text into its own. Reading only the outer message
     * would miss every quota reply that arrives wrapped, which is the failure this classification exists to catch.
     *
     * <p>
     * Each message is stripped of its angle brackets on its own, before being joined, rather than the joined text being
     * stripped once: {@code <} is matched to end-of-string when nothing closes it, so stripping afterwards would let a
     * malformed address in an OUTER message swallow the nested reply that follows it and turn a genuine account refusal
     * back into an ordinary per-row failure.
     *
     * <p>
     * Bounded rather than walked to the end: a cause chain that loops back on itself would spin here forever, inside
     * the send path of every row of every batch. No real chain approaches {@link #MAX_CAUSE_DEPTH} frames, so the bound
     * costs nothing a real reply would notice.
     */
    private static String replyText(Throwable failure) {

        var text = new StringBuilder();
        var cause = failure;
        for (var depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            text.append(String.valueOf(cause.getMessage()).replaceAll("<[^>]*(>|$)", "")).append(' ');
            cause = cause.getCause();
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    /** Never echoes {@code value} itself in the exception message - only the field name, never the content. */
    private static String requireNoControlCharacters(String value, String fieldName) {

        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new MailSendException("The " + fieldName + " contains a control character and was refused");
        }
        return value;
    }

    /**
     * One connected conversation. {@code sendMessage} rather than {@code JavaMailSender#send} because the connection is
     * already open and must stay open for the next row; the assembly above it is byte for byte what the per-message
     * path did.
     */
    private final class SmtpBatch implements MailBatch {

        private final Transport transport;
        private final JavaMailSenderImpl sender;
        private final MailServerSettings settings;
        private final String fingerprint;

        private SmtpBatch(Transport transport, JavaMailSenderImpl sender, MailServerSettings settings) {
            this.transport = transport;
            this.sender = sender;
            this.settings = settings;
            this.fingerprint = settings.fingerprint();
        }

        /**
         * A connection that is gone is a property of the batch, not of the row that happened to be in flight when it
         * went: the provider dropping an idle socket, closing on a messages-per-connection cap, or a network blip
         * raises a {@code MessagingException} that names no limit at all ("Can't send command to host", "Connection
         * closed"), and every remaining row would raise the identical one. Left as an ordinary per-row failure, one
         * blip would burn an attempt on each of them toward {@code maxAttempts} for messages nothing ever refused -
         * where the pre-batch code, one connection per message, burned exactly one. It is therefore raised as a
         * {@link MailAccountRefusedException}, the same way {@link SmtpDelivery#open} raises a refused connection, so
         * the caller holds the mail (see {@link MailHold}) and releases it unmarked.
         *
         * <p>
         * The reply itself is deliberately not lifted into the message of the exception this class authors - a rejected
         * reply routinely echoes the recipient back - so it survives only as the cause, which no log line here ever
         * prints. {@link SmtpDelivery#isAccountLimit} still reads it, through that cause.
         *
         * <p>
         * {@code sendMessage} relies on {@code MimeMessage#writeTo} calling {@code saveChanges()} for an unsaved
         * message, where {@code JavaMailSenderImpl.doSend} called it explicitly before handing the message over. That
         * is what sets MIME-Version, the multipart Content-Type and its boundary, Date and Message-ID; no test here can
         * catch it regressing, because a test reading the message back performs the same implicit save.
         */
        @Override
        public void send(OutgoingMail mail) {

            var message = assemble(mail, sender, settings);
            metrics.run(Integration.SMTP, () -> deliver(message));
            unreported.compareAndSet(null, about(MailOutcome.accepted()));
            // After the send and never before it: metrics.run rethrows whatever deliver threw, so this line is
            // reached only for a message the transport accepted, which is all that is being claimed - what the
            // provider does with it afterwards never comes back down this connection. Counted inline rather than
            // deferred to a commit, even though OutboxRowProcessor.process holds a transaction open around this
            // call: the message has already left, and rolling that row back cannot un-send it, so waiting for the
            // commit would drop the count for a message the transport did accept.
            usageCounters.record(UsageCounters.Event.EMAIL_SENT);
        }

        private void deliver(MimeMessage message) {

            try {
                transport.sendMessage(message, message.getAllRecipients());
            } catch (MessagingException e) {
                throw refusalOf(e);
            }
        }

        /**
         * A recipient or one message refused stays that row's failure; a sender refused after DATA is noted for
         * {@link SmtpDelivery#reportOutcome}, which tells the server only when nothing of the batch was accepted.
         */
        private RuntimeException refusalOf(MessagingException e) {

            if (!transport.isConnected()) {
                unreported.set(about(MailOutcome.refused(MailFailures.reasonOf(e))));
                return new MailAccountRefusedException("the mail transport connection is gone", e);
            }
            if (MailFailures.refusesEveryMessage(e)) {
                unreported.set(about(MailFailures.outcomeOf(e)));
                return new MailAccountRefusedException("the mail server refused the sending account", e);
            }
            var failure = new MailSendException("the transport refused the message", e);
            if (isAccountLimit(failure)) {
                unreported.set(about(MailOutcome.refused(MailRefusal.SENDER_REFUSED)));
                return new MailAccountRefusedException("the transport refused on an account limit", failure);
            }
            if (MailFailures.reasonOf(e) == MailRefusal.SENDER_REFUSED) {
                senderRefusedAfterData.set(about(MailOutcome.refused(MailRefusal.SENDER_REFUSED)));
            }
            return failure;
        }

        private DeliveryOutcome about(MailOutcome outcome) {

            return new DeliveryOutcome(fingerprint, outcome);
        }

        /**
         * A failure closing must never mask a batch that sent correctly: every message already left, and the only
         * consequence of a socket that did not shut down cleanly is one connection the provider reaps on its own.
         */
        @Override
        public void close() {

            try {
                transport.close();
            } catch (MessagingException e) {
                log.warn("Closing the mail transport failed: {}", e.getClass().getSimpleName());
            }
        }
    }
}
