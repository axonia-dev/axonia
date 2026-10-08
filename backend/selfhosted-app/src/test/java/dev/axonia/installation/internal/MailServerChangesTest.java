package dev.axonia.installation.internal;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.MailSecurity;

import static org.assertj.core.api.Assertions.assertThat;

class MailServerChangesTest {

    private static final MailServerRow STORED = MailServerRow.builder().host("smtp.old.test").port(587)
            .security(MailSecurity.STARTTLS).username("relay").passwordSecret("v1:bff04208:one")
            .fromAddress("axonia@acme.test").revision(3).build();

    @Test
    void recordsEachFieldThatChangedInOrderAndNoSecret() {

        // given
        var next = STORED.toBuilder().host("smtp.acme.test").port(465).security(MailSecurity.TLS)
                .passwordSecret("v1:bff04208:two").fromName("Acme").build();

        // when
        var actual = MailServerChanges.between(STORED, next);

        // then
        assertThat(actual).containsExactly(new MailFieldChange("host", "set", "smtp.old.test", "smtp.acme.test"),
                new MailFieldChange("port", "set", "587", "465"),
                new MailFieldChange("security", "set", "STARTTLS", "TLS"),
                new MailFieldChange("password", "replaced", null, null),
                new MailFieldChange("fromName", "set", null, "Acme"));
    }

    @Test
    void recordsWhatAClearRemovedAndNothingThatWasNeverSet() {

        // when
        var actual = MailServerChanges.between(STORED, MailServerRow.builder().revision(3).build());

        // then
        assertThat(actual).containsExactly(new MailFieldChange("host", "cleared", "smtp.old.test", null),
                new MailFieldChange("port", "cleared", "587", null),
                new MailFieldChange("security", "cleared", "STARTTLS", null),
                new MailFieldChange("username", "cleared", "relay", null),
                new MailFieldChange("password", "cleared", null, null),
                new MailFieldChange("fromAddress", "cleared", "axonia@acme.test", null));
    }

    @Test
    void recordsNothingWhenNothingChanged() {

        assertThat(MailServerChanges.between(STORED, STORED.toBuilder().testedFingerprint("v1:bff04208:x").build()))
                .isEmpty();
    }
}
