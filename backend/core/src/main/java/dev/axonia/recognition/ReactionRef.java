package dev.axonia.recognition;

import dev.axonia.ValidationException;

/**
 * A reaction's identity: exactly one arm is set. A standard emoji is the character itself and needs no catalog row; a
 * custom one is a reference to an approved definition in the same Organization.
 */
public record ReactionRef(ReactionKind kind, String emoji, Long definitionId) {

    /** Bounds what can be stored as an emoji. A grapheme cluster with modifiers is comfortably inside this. */
    private static final int MAX_EMOJI_LENGTH = 32;

    public ReactionRef {
        if (kind == ReactionKind.EMOJI) {
            if (emoji == null || emoji.isBlank() || emoji.length() > MAX_EMOJI_LENGTH || definitionId != null) {
                throw new ValidationException("INVALID_REACTION", "An emoji reaction carries a character and no id");
            }
        } else if (definitionId == null || emoji != null) {
            throw new ValidationException("INVALID_REACTION", "A custom reaction carries an id and no character");
        }
    }
}
