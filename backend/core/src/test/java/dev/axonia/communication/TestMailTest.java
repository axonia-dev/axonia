package dev.axonia.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMailTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Owner <owner@example.com>", "owner@example.com, other@example.com", "owner"})
    void refusesARecipientThatIsNotOneBareAddress(String to) {

        // when / then
        assertThatThrownBy(() -> new TestMail(to, "Test mail from axonia", "Sent by Alice."))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("to must be one bare mail address");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Test mail\r\nBcc: someone@example.com"})
    void refusesASubjectThatIsNotOneLine(String subject) {

        // when / then
        assertThatThrownBy(() -> new TestMail("owner@example.com", subject, "Sent by Alice."))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("subject must be one line of text");
    }

    @Test
    void refusesNoText() {

        // when / then
        assertThatThrownBy(() -> new TestMail("owner@example.com", "Test mail from axonia", null))
                .isInstanceOf(NullPointerException.class).hasMessage("text");
    }

    @Test
    void anEmptyTextIsAText() {

        // when / then
        assertThat(new TestMail("owner@example.com", "Test mail from axonia", "").text()).isEmpty();
    }
}
