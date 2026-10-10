package dev.axonia.communication.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTypeTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void theOpenTypesAreTheOnesTheOpenModulesSend() {

        assertThat(MessageType.open()).extracting(MessageType::code).containsExactly("session_upcoming",
                "session_wrap_up", "session_awaiting_close", "organization_roundup");
    }

    @Test
    void aWellFormedCodeIsATypeWhetherOrNotThisEditionSendsIt() {

        assertThat(MessageType.parse("retired_message_type")).contains(MessageType.of("retired_message_type"));
        assertThat(MessageType.of("session_wrap_up")).isEqualTo(MessageType.SESSION_WRAP_UP);
    }

    /**
     * A type read back from a stored or signed code is the open constant itself, so the open types keep the identity
     * the enum gave them. {@code MessageTypeIdentityGuardTest} still refuses {@code ==}, which a contributed type would
     * not survive.
     */
    @Test
    void anOpenCodeReadsBackAsItsConstant() {

        assertThat(MessageType.open()).allSatisfy(type -> {
            assertThat(MessageType.parse(type.code())).containsSame(type);
            assertThat(MessageType.of(type.code())).isSameAs(type);
        });
    }

    @Test
    void aContributedCodeReadsBackAsAnEqualType() {

        assertThat(MessageType.parse("contributed_type")).contains(MessageType.of("contributed_type"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Session", "session-wrap-up", "1session", "a_code_much_longer_than_any_type_needs"})
    void aMalformedCodeIsNoType(String code) {

        assertThat(MessageType.parse(code)).isEmpty();
        assertThatThrownBy(() -> MessageType.of(code)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a message type code: " + code);
    }

    @Test
    void travelsAsItsCode() {

        assertThat(json.writeValueAsString(MessageType.SESSION_UPCOMING)).isEqualTo("\"session_upcoming\"");
        assertThat(json.readValue("\"organization_roundup\"", MessageType.class))
                .isEqualTo(MessageType.ORGANIZATION_ROUNDUP);
    }
}
