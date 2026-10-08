package dev.axonia.installation.internal;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.communication.MailCredentials;
import dev.axonia.communication.MailSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailServerCandidatesTest {

    private static final MailValues RELAY = MailValues.builder().host("smtp.acme.test").port(25)
            .security(MailSecurity.NONE).fromAddress("axonia@acme.test").build();

    /** The login {@code stored(...)} holds, with no password: what a form sends to keep the stored one. */
    private static final MailValues SAME_SERVER = RELAY.toBuilder().host("smtp.stored.test").port(587)
            .security(MailSecurity.STARTTLS).username("relay").build();

    private static final String ANOTHER_SERVER = "values.password: enter the password again for another server.";

    private final SettingsCipher cipher = new SettingsCipher(TestKeys.current());

    private final MailServerCandidates candidates = candidates(MailServerOverlayProperties.builder().build());

    @Test
    void sendsWithTheRequestedValuesAndKeepsTheStoredPasswordOfALogin() {

        // given
        var stored = stored(cipher.seal(SecretField.MAIL_PASSWORD, "s3cret"));

        // when
        var actual = candidates.settings(SAME_SERVER.toBuilder().fromName("Acme").build(), stored);

        // then
        assertThat(actual.host()).isEqualTo("smtp.stored.test");
        assertThat(actual.fromName()).contains("Acme");
        assertThat(actual.credentials()).contains(new MailCredentials("relay", "s3cret"));
    }

    @Test
    void aRelayWithNoLoginNeverReadsTheStoredPasswordAndClearsIt() {

        // given
        var unreadable = stored(new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "old"));

        // when
        var settings = candidates.settings(RELAY, unreadable);
        var next = candidates.next(RELAY, unreadable, "v1:bff04208:tested");

        // then
        assertThat(settings.credentials()).isEmpty();
        assertThat(next.passwordSecret()).isNull();
        assertThat(next.username()).isNull();
        assertThat(next.testedFingerprint()).isEqualTo("v1:bff04208:tested");
        assertThat(next.revision()).isEqualTo(7);
    }

    @Test
    void aLoginWhoseStoredPasswordCannotBeReadMustEnterItAgain() {

        // given
        var unreadable = stored(new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "old"));

        // then
        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().username("relay").build(), unreadable))
                .isInstanceOfSatisfying(MailServerFieldException.class,
                        refused -> assertThat(refused.getField()).isEqualTo("values.password"))
                .hasMessage("values.password: the stored password cannot be read; enter it again.");
        assertThat(candidates.settings(RELAY.toBuilder().username("relay").password("new").build(), unreadable)
                .credentials()).contains(new MailCredentials("relay", "new"));
    }

    @Test
    void sealsATypedPasswordAndKeepsTheStoredOneOtherwise() {

        // given
        var sealed = cipher.seal(SecretField.MAIL_PASSWORD, "s3cret");
        var stored = stored(sealed);

        // when
        var kept = candidates.next(SAME_SERVER, stored, null);
        var replaced = candidates.next(RELAY.toBuilder().username("relay").password("new").build(), stored, null);

        // then
        assertThat(kept.passwordSecret()).isEqualTo(sealed);
        assertThat(cipher.open(SecretField.MAIL_PASSWORD, replaced.passwordSecret())).contains("new");
    }

    @Test
    void takesALockedFieldFromTheDeploymentAndKeepsItsStoredValue() {

        // given
        var locked = candidates(
                MailServerOverlayProperties.builder().host("smtp.locked.test").password("deployed").build());
        var stored = stored(cipher.seal(SecretField.MAIL_PASSWORD, "s3cret"));
        var requested = SAME_SERVER.toBuilder().host("smtp.ignored.test").password("ignored").build();

        // when
        var settings = locked.settings(requested, stored);
        var next = locked.next(requested, stored, null);

        // then
        assertThat(settings.host()).isEqualTo("smtp.locked.test");
        assertThat(settings.credentials()).contains(new MailCredentials("relay", "deployed"));
        assertThat(next.host()).isEqualTo("smtp.stored.test");
        assertThat(next.passwordSecret()).isEqualTo(stored.row().passwordSecret());
    }

    @Test
    void namesTheFirstFieldMissingInTheOrderOfTheForm() {

        assertThatThrownBy(() -> candidates.settings(MailValues.NONE, nothingStored()))
                .hasMessage("values.host: required.");
        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().port(null).build(), nothingStored()))
                .hasMessage("values.port: required.");
        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().security(null).build(), nothingStored()))
                .hasMessage("values.security: required.");
        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().fromAddress(null).build(), nothingStored()))
                .hasMessage("values.fromAddress: required.");
        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().username("relay").build(), nothingStored()))
                .hasMessage("values.password: required with a username.");
        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().password("s3cret").build(), nothingStored()))
                .hasMessage("values.username: required with a password.");
    }

    @Test
    void refusesAFromAddressDeliveryWouldRefuse() {

        assertThatThrownBy(() -> candidates.settings(RELAY.toBuilder().fromAddress("Acme <axonia@acme.test>").build(),
                nothingStored())).hasMessage("values.fromAddress: must be one bare mail address.");
    }

    /** A stored password never follows the server to a host, port or login the request names. */
    @ParameterizedTest
    @ValueSource(strings = {"host", "port", "username", "security"})
    void aStoredPasswordNeverFollowsTheServerElsewhere(String moved) {

        // given
        var stored = stored(cipher.seal(SecretField.MAIL_PASSWORD, "s3cret"));
        var requested = switch (moved) {
            case "host" -> SAME_SERVER.toBuilder().host("smtp.elsewhere.test");
            case "port" -> SAME_SERVER.toBuilder().port(25);
            case "username" -> SAME_SERVER.toBuilder().username("someone-else");
            default -> SAME_SERVER.toBuilder().security(MailSecurity.NONE);
        };

        // then
        assertThatThrownBy(() -> candidates.settings(requested.build(), stored))
                .isInstanceOfSatisfying(MailServerFieldException.class,
                        refused -> assertThat(refused.getField()).isEqualTo("values.password"))
                .hasMessage(ANOTHER_SERVER);
        assertThatThrownBy(() -> candidates.next(requested.build(), stored, null)).hasMessage(ANOTHER_SERVER);
        assertThat(candidates.settings(requested.password("typed").build(), stored).credentials())
                .map(MailCredentials::password).contains("typed");
    }

    @ParameterizedTest
    @CsvSource({"NONE,NONE", "NONE,STARTTLS", "NONE,TLS", "STARTTLS,TLS", "TLS,STARTTLS"})
    void aStoredPasswordStaysWithItsServerOnAsStrongASecurity(MailSecurity storedSecurity, MailSecurity requested) {

        // given
        var stored = stored(cipher.seal(SecretField.MAIL_PASSWORD, "s3cret"), storedSecurity);

        // when
        var actual = candidates.settings(SAME_SERVER.toBuilder().security(requested).build(), stored);

        // then
        assertThat(actual.credentials()).contains(new MailCredentials("relay", "s3cret"));
    }

    @Test
    void aDeploymentPasswordGoesOnlyToTheServerItIsSetFor() {

        // given
        var locked = candidates(MailServerOverlayProperties.builder().password("deployed").build());
        var stored = stored(null);

        // then
        assertThat(locked.settings(SAME_SERVER.toBuilder().password("ignored").build(), stored).credentials())
                .contains(new MailCredentials("relay", "deployed"));
        assertThatThrownBy(() -> locked.settings(SAME_SERVER.toBuilder().host("smtp.elsewhere.test").build(), stored))
                .isInstanceOfSatisfying(MailServerFieldException.class,
                        refused -> assertThat(refused.getField()).isEqualTo("values.password"))
                .hasMessage("values.password: the deployment sets this password for the current server.");
        assertThatThrownBy(() -> locked.settings(SAME_SERVER.toBuilder().security(MailSecurity.NONE).build(), stored))
                .hasMessage("values.password: the deployment sets this password for the current server.");
    }

    @Test
    void aDraftMissingWhereItGoesIsMissingThatBeforeAnyPasswordIsSent() {

        // given
        var stored = stored(cipher.seal(SecretField.MAIL_PASSWORD, "s3cret"));

        // then
        assertThatThrownBy(() -> candidates.settings(SAME_SERVER.toBuilder().host(null).build(), stored))
                .hasMessage("values.host: required.");
        assertThatThrownBy(() -> candidates.settings(SAME_SERVER.toBuilder().port(null).build(), stored))
                .hasMessage("values.port: required.");
        assertThatThrownBy(() -> candidates.settings(SAME_SERVER.toBuilder().security(null).build(), stored))
                .hasMessage("values.security: required.");
    }

    /** The rules delivery holds the values to, Unicode spaces and C1 controls included. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "host|smtp\u3000acme.test|values.host: must be a host name with no space or control character.",
            "host|smtp\u0085acme.test|values.host: must be a host name with no space or control character.",
            "username|re\u0085lay|values.username: must hold no control character.",
            "fromName|Ac\u0085me|values.fromName: must hold no control character.",
            "password|pa\u0085ss|values.password: must be one line of at most 1024 characters."})
    void refusesWhatDeliveryWouldRefuseNamingTheField(String field, String value, String message) {

        // given
        var requested = switch (field) {
            case "host" -> RELAY.toBuilder().host(value);
            case "username" -> RELAY.toBuilder().username(value).password("s3cret");
            case "fromName" -> RELAY.toBuilder().fromName(value);
            default -> RELAY.toBuilder().username("relay").password(value);
        };

        // then
        assertThatThrownBy(() -> candidates.settings(requested.build(), nothingStored())).hasMessage(message);
    }

    @Test
    void refusesAPasswordLongerThanAnyServerTakes() {

        // given
        var requested = RELAY.toBuilder().username("relay");

        // then
        assertThatThrownBy(() -> candidates.settings(requested.password("p".repeat(1025)).build(), nothingStored()))
                .hasMessage("values.password: must be one line of at most 1024 characters.");
        assertThat(candidates.settings(requested.password("p".repeat(1024)).build(), nothingStored()).credentials())
                .isPresent();
    }

    private MailServerCandidates candidates(MailServerOverlayProperties overlay) {

        return new MailServerCandidates(MailServerOverlay.from(overlay), cipher);
    }

    private ResolvedMailServer stored(String passwordSecret) {

        return stored(passwordSecret, MailSecurity.STARTTLS);
    }

    private ResolvedMailServer stored(String passwordSecret, MailSecurity security) {

        var row = MailServerRow.builder().host("smtp.stored.test").port(587).security(security).username("relay")
                .passwordSecret(passwordSecret).fromAddress("axonia@acme.test").revision(7).build();
        var password = Optional.ofNullable(passwordSecret)
                .flatMap(sealed -> cipher.open(SecretField.MAIL_PASSWORD, sealed));
        return new ResolvedMailServer(MailValues.NONE, row.values(password.orElse(null)),
                passwordSecret != null && password.isEmpty(), row);
    }

    private static ResolvedMailServer nothingStored() {

        return new ResolvedMailServer(MailValues.NONE, MailValues.NONE, false, MailServerRow.builder().build());
    }
}
