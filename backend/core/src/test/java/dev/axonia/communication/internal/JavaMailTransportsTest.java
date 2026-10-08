package dev.axonia.communication.internal;

import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import dev.axonia.communication.MailCredentials;
import dev.axonia.communication.MailSecurity;
import dev.axonia.communication.MailServerSettings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one thing this adapter can get wrong and nothing else would notice: connecting with something other than what the
 * sender it is handed was built with. A fake protocol provider registered on the session, rather than a real socket, is
 * what makes that observable - Jakarta Mail instantiates whatever class the provider names and hands it the host, port
 * and credentials it was asked to connect with, so the connection arguments can be read straight back off it.
 */
class JavaMailTransportsTest {

    @Test
    void connectsWithTheConfiguredHostPortAndCredentials() throws Exception {

        var sender = MailSenders.build(MailServerSettings.builder().host("smtp.example.test").port(2587)
                .security(MailSecurity.NONE).credentials(Optional.of(new MailCredentials("axonia-sender", "a-secret")))
                .fromAddress("axonia@example.test").build(), MailSenders.DELIVERY);
        sender.setSession(sessionServing("smtp"));

        var transport = (StubTransport) new JavaMailTransports().connected(sender);

        assertThat(transport.host).isEqualTo("smtp.example.test");
        assertThat(transport.port).isEqualTo(2587);
        assertThat(transport.user).isEqualTo("axonia-sender");
        assertThat(transport.password).isEqualTo("a-secret");
        assertThat(transport.isConnected()).isTrue();
    }

    /**
     * The sender above was built with {@code smtp}. This is the other half: whatever protocol the sender carries is the
     * one asked for, or a sender built for {@code smtps} would connect over plain {@code smtp}.
     */
    @Test
    void honoursAConfiguredProtocolRatherThanAlwaysAssumingSmtp() throws Exception {

        var sender = new JavaMailSenderImpl();
        sender.setProtocol("smtps");
        sender.setHost("smtp.example.test");
        sender.setSession(sessionServing("smtps"));

        var transport = (StubTransport) new JavaMailTransports().connected(sender);

        assertThat(transport.isConnected()).isTrue();
    }

    /** The stub is registered for {@code protocol} alone, so asking for any other one fails rather than falls back. */
    private static Session sessionServing(String protocol) throws NoSuchProviderException {

        var session = Session.getInstance(new Properties());
        session.setProvider(
                new Provider(Provider.Type.TRANSPORT, protocol, StubTransport.class.getName(), "axonia", "test"));
        return session;
    }

    /** Public, with a public constructor, because Jakarta Mail instantiates it reflectively from the provider above. */
    public static final class StubTransport extends Transport {

        private String host;
        private int port;
        private String user;
        private String password;

        public StubTransport(Session session, URLName urlName) {
            super(session, urlName);
        }

        @Override
        protected boolean protocolConnect(String host, int port, String user, String password) {

            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
            return true;
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) {

            throw new UnsupportedOperationException("this stub only ever proves how the connection was made");
        }
    }
}
