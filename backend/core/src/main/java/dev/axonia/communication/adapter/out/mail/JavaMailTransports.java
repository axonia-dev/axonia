package dev.axonia.communication.adapter.out.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Transport;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import dev.axonia.communication.application.port.out.TransportFactory;

/** Connects with the sender built from the mail server's settings: the one place delivery and the test mail connect. */
@Component
public class JavaMailTransports implements TransportFactory {

    /** Every sender comes from {@link MailSenders#build}, which always sets the protocol. */
    @Override
    public Transport connected(JavaMailSenderImpl sender) throws MessagingException {

        var transport = sender.getSession().getTransport(sender.getProtocol());
        transport.connect(sender.getHost(), sender.getPort(), sender.getUsername(), sender.getPassword());
        return transport;
    }
}
