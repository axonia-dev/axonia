package dev.axonia.installation.application;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.api.MailSecurity;
import dev.axonia.installation.domain.MailValues;

import static org.assertj.core.api.Assertions.assertThat;

class MailServerValuesRequestTest {

    @Test
    void readsBlankTextAsUnsetAndKeepsAPasswordAsTyped() {

        // given
        var request = new MailServerValuesRequest(" smtp.acme.test ", 587, "STARTTLS", " ", " pa ss ",
                "axonia@acme.test", "");

        // when
        var actual = request.values();

        // then
        assertThat(actual).isEqualTo(MailValues.builder().host("smtp.acme.test").port(587)
                .security(MailSecurity.STARTTLS).password(" pa ss ").fromAddress("axonia@acme.test").build());
    }

    @Test
    void readsAnEmptyPasswordAndABlankSecurityAsUnset() {

        // when
        var empty = new MailServerValuesRequest("smtp.acme.test", 25, " ", "relay", "", null, null).values();
        var absent = new MailServerValuesRequest("smtp.acme.test", 25, null, "relay", null, null, null).values();

        // then
        assertThat(empty.security()).isNull();
        assertThat(empty.password()).isNull();
        assertThat(absent.security()).isNull();
        assertThat(absent.password()).isNull();
    }

    @Test
    void neverPrintsThePassword() {

        assertThat(new MailServerValuesRequest("smtp.acme.test", 25, null, "relay", "s3cret", null, null).toString())
                .contains("smtp.acme.test").contains("password=<hidden>").doesNotContain("s3cret");
        assertThat(new MailServerValuesRequest("smtp.acme.test", 25, null, null, null, null, null).toString())
                .contains("password=null");
    }
}
