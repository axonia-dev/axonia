package dev.axonia.communication.api;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailServerSettingsTest {

    private static final MailServerSettings ACME = MailServerSettings.builder().host("smtp.acme.com").port(587)
            .security(MailSecurity.STARTTLS).credentials(Optional.of(new MailCredentials("axonia", "s3cret")))
            .fromAddress("axonia@acme.com").fromName(Optional.of("Acme Communities")).build();

    @Test
    void fingerprintsEveryFieldWithAValueThatNeverMoves() {

        // when / then
        assertThat(ACME.fingerprint()).isEqualTo("20806cddacd008fe1e41c720636ac5f0a53c9bf62ed58957e47b90b6a2299018");
        assertThat(local().build().fingerprint())
                .isEqualTo("27ebb4419f62c8bdf1eaba122c3fbefe9cc7f8e9efeb350af7710f2443bc3e7a");
    }

    @Test
    void aNewPasswordIsANewFingerprint() {

        // given
        var rotated = new MailServerSettings(ACME.host(), ACME.port(), ACME.security(),
                Optional.of(new MailCredentials("axonia", "s3cret2")), ACME.fromAddress(), ACME.fromName());

        // when / then
        assertThat(rotated.fingerprint()).isNotEqualTo(ACME.fingerprint());
    }

    @Test
    void noCharacterCanMoveFromOneFieldToTheNext() {

        // given
        var one = local().credentials(Optional.of(new MailCredentials("ab", "c"))).build();
        var other = local().credentials(Optional.of(new MailCredentials("a", "bc"))).build();

        // when / then
        assertThat(one.fingerprint()).isNotEqualTo(other.fingerprint());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "smtp acme.com", "smtp.acme.com\r\nRCPT TO:<x@acme.com>", "smtp\u0000acme.com"})
    void refusesAHostThatIsNotOneName(String host) {

        // when / then
        assertThatThrownBy(() -> local().host(host).build()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("host must be one host name or address");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65_536})
    void refusesAPortOutOfRange(int port) {

        // when / then
        assertThatThrownBy(() -> local().port(port).build()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("port must be between 1 and 65535");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 65_535})
    void acceptsBothEndsOfThePortRange(int port) {

        // when / then
        assertThat(local().port(port).build().port()).isEqualTo(port);
    }

    @Test
    void refusesNoSecurity() {

        // when / then
        assertThatThrownBy(() -> local().security(null).build()).isInstanceOf(NullPointerException.class)
                .hasMessage("security");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Acme <axonia@acme.com>", "a@acme.com, b@acme.com", "not-an-address", " axonia@acme.com",
            "axonia@acme.com "})
    void refusesAFromAddressThatIsNotOneBareAddress(String fromAddress) {

        // when / then
        assertThatThrownBy(() -> local().fromAddress(fromAddress).build()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fromAddress must be one bare mail address");
    }

    @Test
    void refusesAFromNameOnTwoLines() {

        // when / then
        assertThatThrownBy(() -> local().fromName(Optional.of("Acme\r\nBcc: someone@example.com")).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("fromName must be one line");
    }

    @Test
    void aBlankFromNameIsNoFromNameAndANameLosesItsOuterSpaces() {

        // when / then
        assertThat(local().fromName(Optional.of("   ")).build().fromName()).isEmpty();
        assertThat(local().fromName(Optional.of("  Acme  ")).build().fromName()).contains("Acme");
    }

    @Test
    void anAbsentCredentialOrNameIsEmptyNeverNull() {

        // when
        var actual = new MailServerSettings("localhost", 1025, MailSecurity.NONE, null, "axonia@localhost", null);

        // then
        assertThat(actual.credentials()).isEmpty();
        assertThat(actual.fromName()).isEmpty();
    }

    @Test
    void neverPrintsThePassword() {

        // when / then
        assertThat(ACME.toString()).contains("axonia").doesNotContain("s3cret");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "axo\nnia"})
    void refusesAUsernameThatIsNotOneLine(String username) {

        // when / then
        assertThatThrownBy(() -> new MailCredentials(username, "s3cret")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must be set, on one line");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void refusesNoPassword(String password) {

        // when / then
        assertThatThrownBy(() -> new MailCredentials("axonia", password)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password must be set");
    }

    @Test
    void aLocalAddressIsAMailAddress() {

        // when / then
        assertThat(MailServerSettings.isMailAddress("axonia@localhost")).isTrue();
    }

    private static MailServerSettings.MailServerSettingsBuilder local() {

        return MailServerSettings.builder().host("localhost").port(1025).security(MailSecurity.NONE)
                .fromAddress("axonia@localhost");
    }
}
