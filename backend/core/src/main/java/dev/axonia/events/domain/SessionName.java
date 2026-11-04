package dev.axonia.events.domain;

import java.util.List;
import java.util.stream.Stream;

import dev.axonia.shared.api.error.ValidationException;

/**
 * What a session is called, everywhere it is shown: its own title, then its first titled slot, then its series' title,
 * then "Session". Every reader takes the name from here, so a page, a calendar, a mail and an agent cannot disagree.
 *
 * <p>
 * The session's own title comes first because it is the only one an organizer chose for this session: a slot names a
 * talk, a series names a rhythm.
 */
public final class SessionName {

    /** The width of {@code community_session.title}. */
    public static final int MAX_LENGTH = 255;

    static final String GENERIC = "Session";

    private SessionName() {
    }

    public static String of(String ownTitle, List<String> slotTitles, String seriesTitle) {

        var itsOwn = ofItsOwn(ownTitle, slotTitles);
        if (itsOwn != null) {
            return itsOwn;
        }
        return isNamed(seriesTitle) ? seriesTitle : GENERIC;
    }

    /**
     * The name the session gives itself: its own title, else its first titled slot; null when neither names it. A
     * surface that already states the series beside the session takes this one, so it never prints the series twice.
     */
    public static String ofItsOwn(String ownTitle, List<String> slotTitles) {

        return Stream.concat(Stream.of(ownTitle), slotTitles.stream()).filter(SessionName::isNamed).findFirst()
                .orElse(null);
    }

    /** The session's own title as stored: trimmed, and absent when blank, which clears it. */
    public static String ownTitle(String requested) {

        if (!isNamed(requested)) {
            return null;
        }
        var title = requested.strip();
        if (title.length() > MAX_LENGTH) {
            throw new ValidationException("SESSION_TITLE_TOO_LONG",
                    "A session title is at most " + MAX_LENGTH + " characters");
        }
        return title;
    }

    private static boolean isNamed(String title) {

        return title != null && !title.isBlank();
    }
}
