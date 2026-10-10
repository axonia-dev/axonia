package dev.axonia.communication.api;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailOutcomeTest {

    @Test
    void eachReasonCarriesTheWordTheApiUses() {

        // when
        var actual = Arrays.stream(MailRefusal.values()).map(MailRefusal::code).toList();

        // then
        assertThat(actual).containsExactly("connection_failed", "timeout", "tls_required", "tls_failed",
                "authentication_failed", "sender_refused", "recipient_refused");
    }

    @Test
    void aReplyLosesItsControlCharacters() {

        // when
        var actual = MailOutcome.refused(MailRefusal.TLS_REQUIRED,
                Optional.of("530 5.7.0 Must issue a STARTTLS\u0007 command first\r\n"));

        // then
        assertThat(actual).isEqualTo(new MailOutcome.Refused(MailRefusal.TLS_REQUIRED,
                Optional.of("530 5.7.0 Must issue a STARTTLS command first")));
    }

    @Test
    void aReplyIsCutAtThreeHundredCharacters() {

        // when
        var actual = (MailOutcome.Refused) MailOutcome.refused(MailRefusal.SENDER_REFUSED,
                Optional.of("550 " + "x".repeat(400)));

        // then
        assertThat(actual.reply()).hasValueSatisfying(reply -> assertThat(reply).hasSize(300).startsWith("550 x"));
    }

    @Test
    void aBlankOrMissingReplyIsNoReply() {

        // when / then
        assertThat(new MailOutcome.Refused(MailRefusal.TIMEOUT, Optional.of("\r\n ")))
                .isEqualTo(MailOutcome.refused(MailRefusal.TIMEOUT));
        assertThat(new MailOutcome.Refused(MailRefusal.TIMEOUT, null).reply()).isEmpty();
    }

    @Test
    void aRefusalNeedsItsReasonAndAConfiguredServerItsSettings() {

        // when / then
        assertThatThrownBy(() -> MailOutcome.refused(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("reason");
        assertThatThrownBy(() -> MailServerState.configured(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("settings");
    }

    @Test
    void acceptedAndNotConfiguredAreValues() {

        // when / then
        assertThat(MailOutcome.accepted()).isEqualTo(new MailOutcome.Accepted());
        assertThat(MailServerState.notConfigured()).isEqualTo(new MailServerState.NotConfigured());
    }
}
