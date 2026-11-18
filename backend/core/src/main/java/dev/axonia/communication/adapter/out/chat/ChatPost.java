package dev.axonia.communication.adapter.out.chat;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.axonia.communication.application.note.FormattedNote;
import lombok.Builder;

/**
 * What a post says, before any platform has decided how it looks. One builder per message type fills this in, and one
 * mapper per platform turns it into that product's JSON, so "what a wrap-up says in chat" is decided once rather than
 * once per platform.
 *
 * <p>
 * {@code details} is ordered, because a reader scanning a card reads it in the order it was written.
 */
@Builder
public record ChatPost(String sender, String headline, String whenLine, FormattedNote note, Map<String, String> details,
        String actionLabel, String actionUrl) {

    public static final int MAX_NOTE_LENGTH = 2900;

    public ChatPost {
        note = note == null || note.isEmpty() ? null : note;
        details = details == null ? Map.of() : new LinkedHashMap<>(details);
    }
}
