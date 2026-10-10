package dev.axonia.communication.adapter.out.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import jakarta.mail.MessagingException;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import dev.axonia.communication.api.MailServerSettings;

/** The sender for the settings delivery runs on now, built again only when their fingerprint changes. */
@Component
public class MailSenders {

    /** Bounds one message at about 12 seconds, which the claim-lease arithmetic in axonia-core.properties relies on. */
    static final SmtpTimeouts DELIVERY = new SmtpTimeouts(Duration.ofSeconds(2), Duration.ofSeconds(5),
            Duration.ofSeconds(5));

    private static final String TRUE = "true";
    private static final String CHECK_SERVER_IDENTITY = "mail.smtp.ssl.checkserveridentity";

    private String fingerprint;
    private JavaMailSenderImpl sender;

    synchronized JavaMailSenderImpl senderFor(MailServerSettings settings) {

        var current = settings.fingerprint();
        if (!current.equals(fingerprint)) {
            sender = build(settings, DELIVERY);
            fingerprint = current;
        }
        return sender;
    }

    static JavaMailSenderImpl build(MailServerSettings settings, SmtpTimeouts timeouts) {

        var built = new JavaMailSenderImpl();
        built.setProtocol(JavaMailSenderImpl.DEFAULT_PROTOCOL);
        built.setHost(settings.host());
        built.setPort(settings.port());
        built.setDefaultEncoding(StandardCharsets.UTF_8.name());
        var properties = timeouts.asProperties();
        properties.putAll(securityProperties(settings));
        settings.credentials().ifPresent(credentials -> {
            built.setUsername(credentials.username());
            built.setPassword(credentials.password());
            properties.setProperty("mail.smtp.auth", TRUE);
        });
        built.setJavaMailProperties(properties);
        return built;
    }

    static void setFrom(MimeMessageHelper helper, MailServerSettings settings)
            throws MessagingException, UnsupportedEncodingException {

        var name = settings.fromName();
        if (name.isPresent()) {
            helper.setFrom(settings.fromAddress(), name.get());
        } else {
            helper.setFrom(settings.fromAddress());
        }
    }

    private static Map<String, String> securityProperties(MailServerSettings settings) {

        return switch (settings.security()) {
            case STARTTLS -> Map.of("mail.smtp.starttls.enable", TRUE, "mail.smtp.starttls.required", TRUE,
                    CHECK_SERVER_IDENTITY, TRUE);
            case TLS -> Map.of("mail.smtp.ssl.enable", TRUE, CHECK_SERVER_IDENTITY, TRUE);
            case NONE -> Map.of();
        };
    }

    record SmtpTimeouts(Duration connect, Duration read, Duration write) {

        Properties asProperties() {

            var properties = new Properties();
            properties.setProperty("mail.smtp.connectiontimeout", Long.toString(connect.toMillis()));
            properties.setProperty("mail.smtp.timeout", Long.toString(read.toMillis()));
            properties.setProperty("mail.smtp.writetimeout", Long.toString(write.toMillis()));
            return properties;
        }
    }
}
