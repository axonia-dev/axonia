package dev.axonia.communication.adapter.out.mail;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Optional;

import javax.net.ssl.SSLHandshakeException;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.eclipse.angus.mail.util.SocketConnectException;
import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.MailOutcome;
import dev.axonia.communication.api.MailRefusal;

import static org.assertj.core.api.Assertions.assertThat;

/** Each refusal is built the way Angus 2.0.5 raises it, as read in its SMTPTransport and confirmed by probes. */
class MailFailuresTest {

    @Test
    void aRefusedRecipientIsARecipientRefusalWithTheServersLine() throws Exception {

        var failure = new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress("member@example.com"),
                        "RCPT TO:<member@example.com>", 550,
                        "550 5.1.0 Requested action not taken: mailbox unavailable\n"));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.RECIPIENT_REFUSED,
                Optional.of("550 5.1.0 Requested action not taken: mailbox unavailable")));
        assertThat(MailFailures.refusesEveryMessage(failure)).isFalse();
    }

    @Test
    void aRelayThatRefusesTheRecipientsDomainIsARecipientRefusal() throws Exception {

        var failure = new SendFailedException("Invalid Addresses",
                new SMTPAddressFailedException(new InternetAddress("member@example.org"),
                        "RCPT TO:<member@example.org>", 554, "554 5.7.1 <member@example.org>: Relay access denied\n"));

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.RECIPIENT_REFUSED);
    }

    @Test
    void anUntrustedCertificateIsATlsFailure() {

        var failure = new MessagingException("Could not convert socket to TLS",
                new SSLHandshakeException("PKIX path building failed"));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.TLS_FAILED));
    }

    @Test
    void tlsSpokenToAPlainPortIsATlsFailure() {

        var failure = new MessagingException("Could not connect to SMTP host",
                new javax.net.ssl.SSLException("Unsupported or unrecognized SSL message"));

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.TLS_FAILED);
    }

    @Test
    void aServerWithoutStartTlsIsATlsFailure() {

        var failure = new MessagingException("STARTTLS is required but host does not support STARTTLS");

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.TLS_FAILED);
    }

    @Test
    void aRejectedCredentialIsAnAuthenticationFailureThatRefusesEveryMessage() {

        var failure = new AuthenticationFailedException("535 5.7.8 Authentication credentials invalid\n");

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED,
                Optional.of("535 5.7.8 Authentication credentials invalid")));
        assertThat(MailFailures.refusesEveryMessage(failure)).isTrue();
    }

    @Test
    void anAuthenticationRefusedUntilStartTlsIsTlsRequired() {

        var failure = new AuthenticationFailedException("530 5.7.0 Must issue a STARTTLS command first\n");

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.TLS_REQUIRED);
    }

    @Test
    void aSenderRefusedUntilStartTlsIsTlsRequired() {

        var failure = mailFromRefused(530, "530 5.7.0 Must issue a STARTTLS command first\n");

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.TLS_REQUIRED,
                Optional.of("530 5.7.0 Must issue a STARTTLS command first")));
        assertThat(MailFailures.refusesEveryMessage(failure)).isTrue();
    }

    @Test
    void aSenderRefusedUntilItAuthenticatesIsAnAuthenticationFailure() {

        var failure = mailFromRefused(530, "530 5.7.0 Authentication required\n");

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.AUTHENTICATION_FAILED);
    }

    @Test
    void aRefusedSenderIsASenderRefusalThatRefusesEveryMessage() throws Exception {

        var failure = mailFromRefused(550, "550 5.7.1 Sender address rejected: not owned by user\n");
        failure.setNextException(new SMTPSenderFailedException(new InternetAddress("axonia@acme.com"),
                "MAIL FROM:<axonia@acme.com>", 550, "550 5.7.1 Sender address rejected: not owned by user\n"));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.SENDER_REFUSED,
                Optional.of("550 5.7.1 Sender address rejected: not owned by user")));
        assertThat(MailFailures.refusesEveryMessage(failure)).isTrue();
    }

    @Test
    void aMessageRefusedAfterItsDataIsASenderRefusalOfThatMessageAlone() {

        var failure = new SMTPSendFailedException(".", 554, "554 Message rejected: Email address is not verified\n",
                null, null, null, null);

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.SENDER_REFUSED);
        assertThat(MailFailures.refusesEveryMessage(failure)).isFalse();
    }

    /** A recipient domain's TLS policy arrives at the end of DATA and refuses this message alone. */
    @Test
    void aTlsPolicyReplyAfterDataIsThatRecipientsRefusalAlone() {

        var failure = new SMTPSendFailedException(".", 550, "550 5.7.1 TLS is required by the recipient domain\n", null,
                null, null, null);

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.RECIPIENT_REFUSED,
                Optional.of("550 5.7.1 TLS is required by the recipient domain")));
        assertThat(MailFailures.refusesEveryMessage(failure)).isFalse();
    }

    @Test
    void anAuthenticationCodeAfterDataRefusesThatMessageAlone() {

        var failure = new SMTPSendFailedException(".", 535, "535 5.7.8 Authentication credentials invalid\n", null,
                null, null, null);

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.AUTHENTICATION_FAILED);
        assertThat(MailFailures.refusesEveryMessage(failure)).isFalse();
    }

    @Test
    void aRefusalNamingNoCommandIsNotOneOfTheSender() {

        var failure = new SMTPSendFailedException(null, 451, "451 4.3.0 Try again later\n", null, null, null, null);

        assertThat(MailFailures.refusesEveryMessage(failure)).isFalse();
    }

    @Test
    void aServerThatNeverGreetsIsATimeout() {

        var failure = new MessagingException("Exception reading response",
                new SocketTimeoutException("Read timed out"));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.TIMEOUT));
    }

    @Test
    void aConnectionThatTimesOutIsATimeout() {

        var failure = new MailConnectException(new SocketConnectException("Couldn't connect",
                new SocketTimeoutException("connect timed out"), "smtp.acme.com", 587, 10_000));

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.TIMEOUT);
    }

    @Test
    void aRefusedConnectionIsAConnectionFailure() {

        var failure = new MailConnectException(new SocketConnectException("Couldn't connect",
                new ConnectException("Connection refused"), "smtp.acme.com", 587, 10_000));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    @Test
    void anUnknownHostIsAConnectionFailure() {

        var failure = new MailConnectException(new SocketConnectException("Couldn't connect",
                new UnknownHostException("smtp.acme.invalid"), "smtp.acme.invalid", 25, -1));

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.CONNECTION_FAILED);
    }

    @Test
    void aCauseChainThatLoopsStillEnds() {

        var outer = new MessagingException("Sending failed");
        var inner = new MessagingException("Can't send command to SMTP host");
        outer.setNextException(inner);
        inner.setNextException(outer);

        assertThat(MailFailures.reasonOf(outer)).isEqualTo(MailRefusal.CONNECTION_FAILED);
    }

    @Test
    void theReplyIsTheFirstLineOfTheFirstMessageThatIsOne() {

        var failure = new MessagingException("Sending failed", new AuthenticationFailedException(
                "535-5.7.8 Username and Password not accepted.\n535 5.7.8 Learn more\n"));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED,
                Optional.of("535-5.7.8 Username and Password not accepted.")));
    }

    @Test
    void theReplyIsTheFirstOfTwoDifferentReplyLines() {

        var failure = new MessagingException("451 4.3.0 Try again later",
                new MessagingException("550 5.7.1 Relaying denied"));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(
                MailOutcome.refused(MailRefusal.CONNECTION_FAILED, Optional.of("451 4.3.0 Try again later")));
    }

    @Test
    void aTlsPolicyReplyToTheDataCommandItselfIsThatRecipientsRefusalAlone() {

        var failure = new SMTPSendFailedException("DATA", 554, "554 5.7.1 TLS is required by the recipient domain\n",
                null, null, null, null);

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.RECIPIENT_REFUSED);
    }

    @Test
    void aSenderRefusedForAWeakAuthenticationMechanismIsAnAuthenticationFailure() {

        var failure = mailFromRefused(534, "534 5.7.9 Authentication mechanism is too weak\n");

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.AUTHENTICATION_FAILED);
    }

    /** The host is in the message, and a host named for TLS says nothing about TLS. */
    @Test
    void aRefusedConnectionToAHostNamedForTlsIsAConnectionFailure() {

        var failure = new MailConnectException(new SocketConnectException("Using SocketFactory class",
                new ConnectException("Connection refused"), "tls.acme.invalid", 587, 2_000));

        assertThat(MailFailures.outcomeOf(failure)).isEqualTo(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    @Test
    void anUnknownHostNamedForSslIsAConnectionFailure() {

        var failure = new MessagingException("Unknown SMTP host: smtp.ssl.example.org",
                new UnknownHostException("smtp.ssl.example.org"));

        assertThat(MailFailures.reasonOf(failure)).isEqualTo(MailRefusal.CONNECTION_FAILED);
    }

    @Test
    void aSenderAddressNamedForTlsIsStillASenderRefusal() {

        var bracketed = mailFromRefused(550, "550 5.7.1 <tls@acme.com>: Sender rejected\n");
        var bare = mailFromRefused(550, "550 5.7.1 ssl@acme.com: Sender rejected\n");

        assertThat(MailFailures.reasonOf(bracketed)).isEqualTo(MailRefusal.SENDER_REFUSED);
        assertThat(MailFailures.reasonOf(bare)).isEqualTo(MailRefusal.SENDER_REFUSED);
    }

    @Test
    void aFailureWithNoMessageIsAConnectionFailureWithNoReply() {

        assertThat(MailFailures.outcomeOf(new MessagingException()))
                .isEqualTo(MailOutcome.refused(MailRefusal.CONNECTION_FAILED));
    }

    private static SMTPSendFailedException mailFromRefused(int code, String reply) {

        return new SMTPSendFailedException("MAIL FROM:<axonia@acme.com>", code, reply, null, null, null, null);
    }
}
