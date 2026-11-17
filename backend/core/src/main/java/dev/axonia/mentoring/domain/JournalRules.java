package dev.axonia.mentoring.domain;

import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ValidationException;

/**
 * What a note must carry: a short body, and the words a mentee leaves; and how much one journal holds. Topics are named
 * by {@link ProposalRules#topic}.
 */
public final class JournalRules {

    public static final int MAX_NOTE_LENGTH = 2000;

    public static final int MAX_WORDS_LENGTH = 1000;

    public static final int MAX_TOPICS_PER_PAIRING = 30;

    public static final int MAX_NOTES_PER_PAIRING = 200;

    /** Links of any kind hanging off one topic or one note. */
    public static final int MAX_POINTERS_PER_ENTRY = 10;

    private JournalRules() {
    }

    /** A journal already holding {@code cap} of something takes no more of it until one is removed. */
    public static void requireRoom(int held, int cap) {

        if (held >= cap) {
            throw new ConflictException("MENTORING_JOURNAL_FULL", "This journal is full. Remove something first.");
        }
    }

    public static String note(String raw) {

        var body = raw == null ? "" : raw.strip();
        if (body.isEmpty()) {
            throw new ValidationException("field_invalid", "A note needs a body");
        }
        if (body.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("MENTORING_TOO_LONG", "A note is at most " + MAX_NOTE_LENGTH + " characters");
        }
        return body;
    }

    public static String words(String raw) {

        var body = raw == null ? "" : raw.strip();
        if (body.isEmpty()) {
            throw new ValidationException("field_invalid", "Words need a body");
        }
        if (body.length() > MAX_WORDS_LENGTH) {
            throw new ValidationException("MENTORING_TOO_LONG",
                    "Words are at most " + MAX_WORDS_LENGTH + " characters");
        }
        return body;
    }
}
