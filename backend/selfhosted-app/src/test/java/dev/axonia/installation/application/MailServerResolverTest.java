package dev.axonia.installation.application;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.MailSecurity;
import dev.axonia.communication.api.MailServerState;
import dev.axonia.installation.application.port.out.MailServerSettingsStore;
import dev.axonia.installation.configuration.MailServerOverlayProperties;
import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.MailServerRow;
import dev.axonia.installation.domain.SecretField;
import dev.axonia.installation.domain.TestKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MailServerResolverTest {

    private final MailServerSettingsStore table = mock(MailServerSettingsStore.class);

    private final SettingsCipher cipher = new SettingsCipher(TestKeys.current());

    @Test
    void opensTheStoredPasswordAndLaysTheOverlayOverTheStoredValues() {

        // given
        given(table.read()).willReturn(stored(cipher.seal(SecretField.MAIL_PASSWORD, "s3cret")));
        var resolver = new MailServerResolver(table, cipher,
                MailServerOverlay.from(MailServerOverlayProperties.builder().host("smtp.override.test").build()));

        // when
        var actual = resolver.resolve();

        // then
        assertThat(actual.stored().password()).isEqualTo("s3cret");
        assertThat(actual.passwordUnreadable()).isFalse();
        assertThat(actual.effective().host()).isEqualTo("smtp.override.test");
        assertThat(actual.effective().port()).isEqualTo(587);
        assertThat(actual.state()).isInstanceOf(MailServerState.Configured.class);
        assertThat(actual.row().revision()).isEqualTo(4);
    }

    @Test
    void aStoredPasswordNoKeyOpensIsUnreadableAndSendsNothing() {

        // given
        var sealedElsewhere = new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "s3cret");
        given(table.read()).willReturn(stored(sealedElsewhere));
        var resolver = new MailServerResolver(table, cipher,
                MailServerOverlay.from(MailServerOverlayProperties.builder().build()));

        // when
        var actual = resolver.resolve();

        // then
        assertThat(actual.passwordUnreadable()).isTrue();
        assertThat(actual.stored().password()).isNull();
        assertThat(actual.state()).isInstanceOf(MailServerState.NotConfigured.class);
    }

    @Test
    void aPasswordTheDeploymentSetsMakesAnUnreadableStoredOneIrrelevant() {

        // given
        var sealedElsewhere = new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "s3cret");
        given(table.read()).willReturn(stored(sealedElsewhere));
        var resolver = new MailServerResolver(table, cipher,
                MailServerOverlay.from(MailServerOverlayProperties.builder().password("from-the-deployment").build()));

        // when
        var actual = resolver.resolve();

        // then
        assertThat(actual.passwordUnreadable()).isFalse();
        assertThat(actual.effective().password()).isEqualTo("from-the-deployment");
        assertThat(actual.state()).isInstanceOf(MailServerState.Configured.class);
    }

    @Test
    void nothingStoredAndNothingOverlaidIsNotConfigured() {

        // given
        given(table.read()).willReturn(MailServerRow.builder().revision(0).build());
        var resolver = new MailServerResolver(table, cipher,
                MailServerOverlay.from(MailServerOverlayProperties.builder().build()));

        // when
        var actual = resolver.resolve();

        // then
        assertThat(actual.passwordUnreadable()).isFalse();
        assertThat(actual.state()).isInstanceOf(MailServerState.NotConfigured.class);
    }

    private static MailServerRow stored(String passwordSecret) {

        return MailServerRow.builder().host("smtp.acme.test").port(587).security(MailSecurity.STARTTLS)
                .username("relay").passwordSecret(passwordSecret).fromAddress("axonia@acme.test").revision(4).build();
    }
}
