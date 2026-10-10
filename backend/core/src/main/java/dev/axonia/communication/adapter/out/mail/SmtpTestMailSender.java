package dev.axonia.communication.adapter.out.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import dev.axonia.communication.adapter.out.mail.MailSenders.SmtpTimeouts;
import dev.axonia.communication.api.MailOutcome;
import dev.axonia.communication.api.MailRefusal;
import dev.axonia.communication.api.MailServerSettings;
import dev.axonia.communication.api.TestMail;
import dev.axonia.communication.api.TestMailSender;
import dev.axonia.communication.application.port.out.TransportFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * One test message over the transport delivery uses, on a sender built for the candidate and never cached, with
 * delivery's own socket timeouts: a server too slow for delivery fails its test too. The attempt runs on a virtual
 * thread, whose socket reads an interrupt ends, so the ceiling bounds the whole conversation whatever the server does.
 */
@Component
@Slf4j
class SmtpTestMailSender implements TestMailSender {

    private static final Duration CEILING = Duration.ofSeconds(15);

    private final TransportFactory transports;
    private final SmtpTimeouts timeouts;
    private final Duration ceiling;

    @Autowired
    SmtpTestMailSender(TransportFactory transports) {

        this(transports, MailSenders.DELIVERY, CEILING);
    }

    SmtpTestMailSender(TransportFactory transports, SmtpTimeouts timeouts, Duration ceiling) {

        this.transports = transports;
        this.timeouts = timeouts;
        this.ceiling = ceiling;
    }

    @Override
    public MailOutcome send(MailServerSettings candidate, TestMail mail) {

        var attempt = new FutureTask<>(() -> deliver(candidate, mail));
        Thread.ofVirtual().name("mail-server-test").start(attempt);
        try {
            return attempt.get(ceiling.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            attempt.cancel(true);
            return MailOutcome.refused(MailRefusal.TIMEOUT);
        } catch (InterruptedException e) {
            attempt.cancel(true);
            Thread.currentThread().interrupt();
            return MailOutcome.refused(MailRefusal.TIMEOUT);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The test mail could not be attempted", e.getCause());
        }
    }

    private MailOutcome deliver(MailServerSettings candidate, TestMail mail) throws UnsupportedEncodingException {

        var sender = MailSenders.build(candidate, timeouts);
        try {
            var message = assemble(sender, candidate, mail);
            var transport = transports.connected(sender);
            try {
                transport.sendMessage(message, message.getAllRecipients());
            } finally {
                closeQuietly(transport);
            }
            return MailOutcome.accepted();
        } catch (MessagingException e) {
            return MailFailures.outcomeOf(e);
        }
    }

    private static MimeMessage assemble(JavaMailSenderImpl sender, MailServerSettings candidate, TestMail mail)
            throws MessagingException, UnsupportedEncodingException {

        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        MailSenders.setFrom(helper, candidate);
        helper.setTo(mail.to());
        helper.setSubject(mail.subject());
        helper.setText(mail.text(), false);
        return message;
    }

    private static void closeQuietly(Transport transport) {

        try {
            transport.close();
        } catch (MessagingException e) {
            log.debug("Closing the test mail transport failed: {}", e.getClass().getSimpleName());
        }
    }
}
