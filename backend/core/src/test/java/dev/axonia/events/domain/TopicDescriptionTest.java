package dev.axonia.events.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicDescriptionTest {

    @Test
    @DisplayName("an omitted description is stored as the empty string")
    void omittedIsEmpty() {

        assertThat(TopicDescription.of(null)).isEmpty();
    }

    @Test
    @DisplayName("a description at the cap is kept as written")
    void atTheCapIsKept() {

        var atTheCap = "x".repeat(TopicDescription.MAX_LENGTH);

        assertThat(TopicDescription.of(atTheCap)).isEqualTo(atTheCap);
    }

    @Test
    @DisplayName("a description over the cap is refused with a message telling the writer to shorten it")
    void overTheCapIsRefused() {

        assertThatThrownBy(() -> TopicDescription.of("x".repeat(TopicDescription.MAX_LENGTH + 1)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("at most 500 characters")
                .extracting("code").isEqualTo("TOPIC_DESCRIPTION_TOO_LONG");
    }
}
