package dev.axonia.installation.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import dev.axonia.communication.MailSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailServerOverlayTest {

    @Test
    void bindsEveryVariableByTheNameTheDocumentationGives() {

        // given
        var environment = new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("AXONIA_MAIL_HOST", "smtp.acme.test", "AXONIA_MAIL_PORT", "465", "AXONIA_MAIL_SECURITY", "tls",
                        "AXONIA_MAIL_USERNAME", "relay", "AXONIA_MAIL_PASSWORD", "s3cret", "AXONIA_MAIL_FROM_ADDRESS",
                        "axonia@acme.test", "AXONIA_MAIL_FROM_NAME", "Acme Communities"));

        // when
        var actual = MailServerOverlay.from(new Binder(ConfigurationPropertySources.from(environment))
                .bind("axonia.mail", MailServerOverlayProperties.class).get());

        // then
        assertThat(actual.values()).isEqualTo(
                MailValues.builder().host("smtp.acme.test").port(465).security(MailSecurity.TLS).username("relay")
                        .password("s3cret").fromAddress("axonia@acme.test").fromName("Acme Communities").build());
        assertThat(actual.lockedBy(MailField.FROM_ADDRESS)).isEqualTo("AXONIA_MAIL_FROM_ADDRESS");
    }

    @Test
    void locksOnlyTheFieldsAVariableSetsAndReadsAnEmptyOneAsUnset() {

        // when
        var actual = MailServerOverlay.from(MailServerOverlayProperties.builder().host(" smtp.acme.test ").port("")
                .username("   ").password("").fromName("Acme").build());

        // then
        assertThat(actual.locks(MailField.HOST)).isTrue();
        assertThat(actual.values().host()).isEqualTo("smtp.acme.test");
        assertThat(actual.lockedBy(MailField.FROM_NAME)).isEqualTo("AXONIA_MAIL_FROM_NAME");
        assertThat(actual.locks(MailField.PORT)).isFalse();
        assertThat(actual.locks(MailField.USERNAME)).isFalse();
        assertThat(actual.lockedBy(MailField.PASSWORD)).isNull();
    }

    @Test
    void keepsAPasswordAsWrittenAndNeverPrintsIt() {

        // when
        var actual = MailServerOverlayProperties.builder().password(" pa ss ").build();

        // then
        assertThat(MailServerOverlay.from(actual).values().password()).isEqualTo(" pa ss ");
        assertThat(actual.toString()).contains("password=<hidden>").doesNotContain("pa ss");
        assertThat(MailServerOverlayProperties.builder().build().toString()).contains("password=null");
    }

    /** A Secret made with --from-file keeps the file's trailing newline, which no server would accept. */
    @Test
    void refusesAPasswordOnMoreThanOneLineNamingTheVariableAndNeverTheValue() {

        // given
        var properties = MailServerOverlayProperties.builder().password("s3cret\n").build();

        // then
        assertThatThrownBy(() -> MailServerOverlay.from(properties)).isInstanceOf(IllegalStateException.class)
                .hasMessage("AXONIA_MAIL_PASSWORD must be one line.");
    }

    @Test
    void refusesAHostWithAControlCharacterNamingTheVariable() {

        // given
        var properties = MailServerOverlayProperties.builder().host("smtp" + (char) 7 + "acme.test").build();

        // then
        assertThatThrownBy(() -> MailServerOverlay.from(properties))
                .hasMessage("AXONIA_MAIL_HOST must be a host name with no space or control character.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "host|smtp acme.test|AXONIA_MAIL_HOST must be a host name with no space or control character.",
            "host|smtp\u3000acme.test|AXONIA_MAIL_HOST must be a host name with no space or control character.",
            "host|smtp\u0085acme.test|AXONIA_MAIL_HOST must be a host name with no space or control character.",
            "username|re\u0085lay|AXONIA_MAIL_USERNAME must hold no control character.",
            "fromName|Ac\u0085me|AXONIA_MAIL_FROM_NAME must hold no control character.",
            "port|25x|AXONIA_MAIL_PORT must be a port number from 1 to 65535.",
            "port|0|AXONIA_MAIL_PORT must be a port number from 1 to 65535.",
            "port|65536|AXONIA_MAIL_PORT must be a port number from 1 to 65535.",
            "security|SSL|AXONIA_MAIL_SECURITY must be STARTTLS, TLS or NONE.",
            "username|re\tlay|AXONIA_MAIL_USERNAME must hold no control character.",
            "fromAddress|axonia|AXONIA_MAIL_FROM_ADDRESS must be a mail address.",
            "fromName|Ac\tme|AXONIA_MAIL_FROM_NAME must hold no control character."})
    void refusesAMalformedValueNamingTheVariableAndNeverTheValue(String field, String value, String message) {

        // given
        var properties = switch (field) {
            case "host" -> MailServerOverlayProperties.builder().host(value);
            case "port" -> MailServerOverlayProperties.builder().port(value);
            case "security" -> MailServerOverlayProperties.builder().security(value);
            case "username" -> MailServerOverlayProperties.builder().username(value);
            case "fromAddress" -> MailServerOverlayProperties.builder().fromAddress(value);
            default -> MailServerOverlayProperties.builder().fromName(value);
        };

        // then
        assertThatThrownBy(() -> MailServerOverlay.from(properties.build())).isInstanceOf(IllegalStateException.class)
                .hasMessage(message);
    }
}
