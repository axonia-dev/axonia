package dev.axonia.communication.internal;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.MimeMessageHelper;

import dev.axonia.communication.MailCredentials;
import dev.axonia.communication.MailSecurity;
import dev.axonia.communication.MailServerSettings;

import static org.assertj.core.api.Assertions.assertThat;

class MailSendersTest {

    private static final MailServerSettings STARTTLS = MailServerSettings.builder().host("smtp.acme.com").port(587)
            .security(MailSecurity.STARTTLS).credentials(Optional.of(new MailCredentials("axonia", "s3cret")))
            .fromAddress("axonia@acme.com").build();

    private final MailSenders senders = new MailSenders();

    @Test
    void reusesOneSenderWhileTheFingerprintStaysTheSame() {

        // given
        var first = senders.senderFor(RecordingMailServer.LOCAL);
        var sameValues = MailServerSettings.builder().host("localhost").port(1025).security(MailSecurity.NONE)
                .fromAddress("axonia@localhost").build();

        // when
        var actual = senders.senderFor(sameValues);

        // then
        assertThat(actual).isSameAs(first);
    }

    @Test
    void buildsANewSenderWhenAnyFieldChanges() {

        // given
        var first = senders.senderFor(STARTTLS);
        var rotated = new MailServerSettings(STARTTLS.host(), STARTTLS.port(), STARTTLS.security(),
                Optional.of(new MailCredentials("axonia", "s3cret2")), STARTTLS.fromAddress(), STARTTLS.fromName());

        // when
        var actual = senders.senderFor(rotated);

        // then
        assertThat(actual).isNotSameAs(first);
        assertThat(actual.getPassword()).isEqualTo("s3cret2");
    }

    @Test
    void aPlainServerGetsTheDeliveryTimeoutsAndNothingElse() {

        // when
        var actual = MailSenders.build(RecordingMailServer.LOCAL, MailSenders.DELIVERY);

        // then
        assertThat(actual.getProtocol()).isEqualTo("smtp");
        assertThat(actual.getHost()).isEqualTo("localhost");
        assertThat(actual.getPort()).isEqualTo(1025);
        assertThat(actual.getUsername()).isNull();
        assertThat(actual.getDefaultEncoding()).isEqualTo("UTF-8");
        assertThat(actual.getJavaMailProperties()).isEqualTo(properties(Map.of("mail.smtp.connectiontimeout", "2000",
                "mail.smtp.timeout", "5000", "mail.smtp.writetimeout", "5000")));
    }

    @Test
    void startTlsIsRequiredChecksTheServersNameAndAuthenticates() {

        // when
        var actual = MailSenders.build(STARTTLS, MailSenders.DELIVERY);

        // then
        assertThat(actual.getUsername()).isEqualTo("axonia");
        assertThat(actual.getPassword()).isEqualTo("s3cret");
        assertThat(actual.getJavaMailProperties()).containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true").containsEntry("mail.smtp.auth", "true")
                .doesNotContainKey("mail.smtp.ssl.enable");
    }

    @Test
    void tlsIsTlsFromTheFirstByte() {

        // given
        var tls = MailServerSettings.builder().host("smtp.acme.com").port(465).security(MailSecurity.TLS)
                .fromAddress("axonia@acme.com").build();

        // when
        var actual = MailSenders.build(tls, MailSenders.DELIVERY);

        // then
        assertThat(actual.getJavaMailProperties()).containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .doesNotContainKeys("mail.smtp.starttls.enable", "mail.smtp.auth");
    }

    @Test
    void setsTheFromAddressAloneOrWithItsName() throws Exception {

        // given
        var bare = new MimeMessageHelper(new MimeMessage(Session.getInstance(new Properties())));
        var named = new MimeMessageHelper(new MimeMessage(Session.getInstance(new Properties())));

        // when
        MailSenders.setFrom(bare, RecordingMailServer.LOCAL);
        MailSenders.setFrom(named, MailServerSettings.builder().host("localhost").port(1025).security(MailSecurity.NONE)
                .fromAddress("axonia@localhost").fromName(Optional.of("Acme")).build());

        // then
        assertThat(((InternetAddress) bare.getMimeMessage().getFrom()[0]).getPersonal()).isNull();
        assertThat(((InternetAddress) named.getMimeMessage().getFrom()[0]).getPersonal()).isEqualTo("Acme");
        assertThat(((InternetAddress) named.getMimeMessage().getFrom()[0]).getAddress()).isEqualTo("axonia@localhost");
    }

    private static Properties properties(Map<String, String> entries) {

        var properties = new Properties();
        properties.putAll(entries);
        return properties;
    }
}
