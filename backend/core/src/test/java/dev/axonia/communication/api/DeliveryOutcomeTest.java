package dev.axonia.communication.api;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryOutcomeTest {

    private static final MailServerSettings USED = withPassword("old-password");

    @Test
    void isAboutTheSettingsDeliveryUsedAndNoOthers() {

        // given
        var replaced = withPassword("new-password");

        // when
        var actual = DeliveryOutcome.of(USED, MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED));

        // then
        assertThat(actual.settingsFingerprint()).isEqualTo(USED.fingerprint());
        assertThat(actual.isAbout(USED)).isTrue();
        assertThat(actual.isAbout(replaced)).isFalse();
    }

    @Test
    void namesItsSettingsAndItsOutcome() {

        // when / then
        assertThatThrownBy(() -> new DeliveryOutcome(null, MailOutcome.accepted()))
                .isInstanceOf(NullPointerException.class).hasMessage("settingsFingerprint");
        assertThatThrownBy(() -> new DeliveryOutcome(USED.fingerprint(), null)).isInstanceOf(NullPointerException.class)
                .hasMessage("outcome");
    }

    @Test
    void printsItsOutcomeAndNeverTheFingerprint() {

        // given
        var outcome = DeliveryOutcome.of(USED, MailOutcome.accepted());

        // when
        var actual = outcome.toString();

        // then
        assertThat(actual).isEqualTo("DeliveryOutcome[outcome=" + MailOutcome.accepted() + "]")
                .doesNotContain(USED.fingerprint());
    }

    private static MailServerSettings withPassword(String password) {

        return MailServerSettings.builder().host("smtp.acme.test").port(587).security(MailSecurity.STARTTLS)
                .credentials(Optional.of(new MailCredentials("relay", password))).fromAddress("axonia@acme.test")
                .build();
    }
}
