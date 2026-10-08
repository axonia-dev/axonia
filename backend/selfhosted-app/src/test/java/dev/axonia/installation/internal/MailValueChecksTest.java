package dev.axonia.installation.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailValueChecksTest {

    @Test
    void namesTheFirstMalformedFieldInTheOrderOfTheForm() {

        // given
        var malformed = MailValuesTest.COMPLETE.toBuilder().host("smtp acme.test").fromName("Ac\u0085me").build();

        // then
        assertThatThrownBy(() -> MailValueChecks.checkFormats(malformed))
                .isInstanceOfSatisfying(MailServerFieldException.class,
                        refused -> assertThat(refused.getField()).isEqualTo("values.host"))
                .hasMessage("values.host: must be a host name with no space or control character.");
        assertThatNoException().isThrownBy(() -> MailValueChecks.checkFormats(MailValues.NONE));
        assertThatNoException().isThrownBy(() -> MailValueChecks.checkFormats(MailValuesTest.COMPLETE));
    }

    @Test
    void namesTheFirstMissingFieldThenTheHalfOfACredential() {

        assertThat(MailValueChecks.missing(MailValues.NONE)).hasMessage("values.host: required.");
        assertThat(MailValueChecks.missing(MailValuesTest.COMPLETE.toBuilder().security(null).build()))
                .hasMessage("values.security: required.");
        assertThat(MailValueChecks.missing(MailValuesTest.COMPLETE.toBuilder().username(null).build()))
                .hasMessage("values.username: required with a password.");
        assertThat(MailValueChecks.missing(MailValuesTest.COMPLETE.toBuilder().password(null).build()))
                .hasMessage("values.password: required with a username.");
    }

    @Test
    void takesAPasswordOfOneLineUpToItsLength() {

        assertThatNoException().isThrownBy(() -> MailValueChecks.checkPassword(null));
        assertThatNoException().isThrownBy(() -> MailValueChecks.checkPassword(" pa ss ".repeat(146)));
        assertThatThrownBy(() -> MailValueChecks.checkPassword("p".repeat(1025)))
                .hasMessage("values.password: must be one line of at most 1024 characters.");
        assertThatThrownBy(() -> MailValueChecks.checkPassword("pa\tss"))
                .hasMessage("values.password: must be one line of at most 1024 characters.");
    }
}
