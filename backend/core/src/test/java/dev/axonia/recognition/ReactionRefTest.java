package dev.axonia.recognition;

import org.junit.jupiter.api.Test;

import dev.axonia.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionRefTest {

    @Test
    void aStandardEmojiIsValid() {

        var ref = new ReactionRef(ReactionKind.EMOJI, "🧠", null);

        assertThat(ref.emoji()).isEqualTo("🧠");
        assertThat(ref.definitionId()).isNull();
    }

    @Test
    void aCustomReactionIsValid() {

        var ref = new ReactionRef(ReactionKind.CUSTOM, null, 5L);

        assertThat(ref.definitionId()).isEqualTo(5L);
        assertThat(ref.emoji()).isNull();
    }

    @Test
    void anEmojiReactionWithNoCharacterIsRejected() {

        assertThatThrownBy(() -> new ReactionRef(ReactionKind.EMOJI, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void anEmojiReactionWithABlankCharacterIsRejected() {

        assertThatThrownBy(() -> new ReactionRef(ReactionKind.EMOJI, "   ", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void anEmojiReactionLongerThanBoundsIsRejected() {

        assertThatThrownBy(() -> new ReactionRef(ReactionKind.EMOJI, "x".repeat(33), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void anEmojiReactionCarryingADefinitionIdIsRejected() {

        assertThatThrownBy(() -> new ReactionRef(ReactionKind.EMOJI, "🧠", 5L)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aCustomReactionWithNoDefinitionIdIsRejected() {

        assertThatThrownBy(() -> new ReactionRef(ReactionKind.CUSTOM, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aCustomReactionCarryingACharacterIsRejected() {

        assertThatThrownBy(() -> new ReactionRef(ReactionKind.CUSTOM, "🧠", 5L))
                .isInstanceOf(ValidationException.class);
    }
}
