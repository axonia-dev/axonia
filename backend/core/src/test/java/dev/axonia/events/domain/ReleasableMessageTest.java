package dev.axonia.events.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.communication.api.MessageType;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The preview endpoint takes the message type as a parameter, which puts the whole weight of "and nothing else" on
 * {@link ReleasableMessage#of}. A default here would be the worst possible failure: it passes every happy-path test
 * while showing an organizer one message under a URL claiming another, so every way of reaching one is named below.
 */
class ReleasableMessageTest {

    @ParameterizedTest
    @EnumSource(ReleasableMessage.class)
    void everyReleasableMessageResolvesFromItsOwnWireCode(ReleasableMessage message) {

        assertThat(ReleasableMessage.of(message.type().code())).isSameAs(message);
    }

    /**
     * The organizer-only awaiting-close nudge is a perfectly real {@link MessageType} with a perfectly real template,
     * and it stays automatic - no intent, no card, nobody to preview it for. It is refused for the same reason nonsense
     * is, because "a message type that exists" was never the question.
     */
    @Test
    void aRealMessageTypeNoOrganizerReleasesIsRefused() {

        assertThatThrownBy(() -> ReleasableMessage.of(MessageType.SESSION_AWAITING_CLOSE.code()))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UNSUPPORTED_MESSAGE_TYPE");
    }

    /**
     * {@code SESSION_WRAP_UP} is the Java constant name, not the wire code, and it is the single likeliest wrong value
     * a client sends. It must be refused rather than resolved leniently: {@link MessageType#code} is the canonical wire
     * representation everywhere else on this API, and accepting a second spelling here would make this the one endpoint
     * where the wire vocabulary is different.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SESSION_WRAP_UP", "WRAP_UP", "session-wrap-up", "wrap_up", "", "banana"})
    void anythingThatIsNotAReleasableMessagesWireCodeIsRefused(String code) {

        assertThatThrownBy(() -> ReleasableMessage.of(code)).isInstanceOf(ValidationException.class).extracting("code")
                .isEqualTo("UNSUPPORTED_MESSAGE_TYPE");
    }

    /**
     * The template basename is the one literal the producer and the preview must agree on, so it is pinned here rather
     * than only where each of them uses it: a rename that updated only one of the two call sites would otherwise leave
     * a preview rendering a different template than the send it claims to show.
     */
    @Test
    void eachMessageCarriesTheTemplateItsProducerEnqueues() {

        assertThat(ReleasableMessage.UPCOMING.template()).isEqualTo("session-upcoming");
        assertThat(ReleasableMessage.WRAP_UP.template()).isEqualTo("session-wrap-up");
    }
}
