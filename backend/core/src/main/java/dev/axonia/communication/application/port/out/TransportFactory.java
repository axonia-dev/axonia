package dev.axonia.communication.application.port.out;

import jakarta.mail.MessagingException;
import jakarta.mail.Transport;

import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * The one place delivery and the test mail connect to JavaMail's own transport, so that both can be driven from a test
 * without standing up an SMTP server. {@code JavaMailTransports} is the one production implementation.
 */
@FunctionalInterface
public interface TransportFactory {

    /**
     * A transport already connected and authenticated, never one the caller still has to connect: everything that can
     * be refused about the account rather than about a message - a rejected credential, an unreachable host - is
     * refused here, before any message is sent.
     */
    Transport connected(JavaMailSenderImpl sender) throws MessagingException;
}
