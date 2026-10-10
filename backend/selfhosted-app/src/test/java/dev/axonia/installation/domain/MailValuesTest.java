package dev.axonia.installation.domain;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.axonia.communication.api.MailCredentials;
import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerState;

import static org.assertj.core.api.Assertions.assertThat;

public class MailValuesTest {

    public static final MailValues COMPLETE = MailValues.builder().host("smtp.acme.test").port(587)
            .security(MailSecurity.STARTTLS).username("relay").password("s3cret").fromAddress("axonia@acme.test")
            .fromName("Acme").build();

    @Test
    void takesEachValueThatIsSetAndTheBasesWhereNot() {

        // given
        var overlay = MailValues.builder().host("smtp.override.test").fromName("Override").build();

        // when
        var actual = overlay.over(COMPLETE);

        // then
        assertThat(actual).isEqualTo(COMPLETE.toBuilder().host("smtp.override.test").fromName("Override").build());
        assertThat(MailValues.NONE.over(COMPLETE)).isEqualTo(COMPLETE);
    }

    @Test
    void isConfiguredWithEveryRequiredValueAndCredentialsInPairs() {

        // when
        var actual = COMPLETE.state();

        // then
        assertThat(actual).isInstanceOfSatisfying(MailServerState.Configured.class, configured -> {
            var settings = configured.settings();
            assertThat(settings.host()).isEqualTo("smtp.acme.test");
            assertThat(settings.port()).isEqualTo(587);
            assertThat(settings.security()).isEqualTo(MailSecurity.STARTTLS);
            assertThat(settings.credentials()).contains(new MailCredentials("relay", "s3cret"));
            assertThat(settings.fromAddress()).isEqualTo("axonia@acme.test");
            assertThat(settings.fromName()).contains("Acme");
        });
        assertThat(COMPLETE.toBuilder().username(null).password(null).fromName(null).build().settings())
                .hasValueSatisfying(settings -> {
                    assertThat(settings.credentials()).isEmpty();
                    assertThat(settings.fromName()).isEmpty();
                });
    }

    static Stream<MailValues> incomplete() {

        return Stream.of(COMPLETE.toBuilder().host(null).build(), COMPLETE.toBuilder().port(null).build(),
                COMPLETE.toBuilder().security(null).build(), COMPLETE.toBuilder().fromAddress(null).build(),
                COMPLETE.toBuilder().password(null).build(), COMPLETE.toBuilder().username(null).build(),
                MailValues.NONE);
    }

    @ParameterizedTest
    @MethodSource("incomplete")
    void isNotConfiguredWithoutARequiredValueOrWithHalfACredential(MailValues values) {

        assertThat(values.settings()).isEmpty();
        assertThat(values.state()).isInstanceOf(MailServerState.NotConfigured.class);
    }

    @Test
    void namesEachFieldsValue() {

        assertThat(Stream.of(MailField.values()).map(COMPLETE::value)).containsExactly("smtp.acme.test", 587,
                MailSecurity.STARTTLS, "relay", "s3cret", "axonia@acme.test", "Acme");
        assertThat(Stream.of(MailField.values()).map(MailField::apiName)).containsExactly("host", "port", "security",
                "username", "password", "fromAddress", "fromName");
    }

    @Test
    void neverPrintsThePassword() {

        assertThat(COMPLETE.toString()).contains("smtp.acme.test").contains("password=<hidden>")
                .doesNotContain("s3cret");
        assertThat(MailValues.NONE.toString()).contains("password=null");
    }

    @Test
    void takesTheFieldsNamedFromTheOtherValues() {

        // when
        var actual = COMPLETE.with(field -> field == MailField.HOST || field == MailField.PASSWORD, MailValues.NONE);

        // then
        assertThat(actual).isEqualTo(COMPLETE.toBuilder().host(null).password(null).build());
    }
}
