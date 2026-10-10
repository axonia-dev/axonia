package dev.axonia.communication.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * When a session is, on the clock of whoever is reading about it.
 *
 * <p>
 * Every session message used to state its hour in UTC. That is correct and nobody lives on it: a member in Berlin was
 * told 16:00 for a session starting at 18:00 where they sit, and had to do the arithmetic the product already had every
 * fact to do for them. The recipient's own clock is resolved at send time, beside the address that was already being
 * resolved there, and travels in the facts as {@code readerTimeZone}.
 *
 * <p>
 * {@code readerTimeZone} is absent on the surfaces that have no single reader - a chat post reaches a room, not a
 * person - and there the session's own clock is used instead. That is not a compromise but the only true answer for
 * those surfaces, and it is still not UTC.
 *
 * <p>
 * The session's own hour is named alongside only when the two clocks genuinely differ at that instant, compared by
 * offset rather than by zone id: a Paris reader and a Berlin session share a wall clock, and "18:00 in Paris (18:00 in
 * Berlin)" states one fact twice.
 *
 * <p>
 * An untimed occurrence is the exception that skips all of this. It stores midnight in its series' zone, which is an
 * ordinary instant no reader can tell from a chosen one, so it names the day alone and does so on the clock that
 * decided which day it is - the session's, never the reader's, or a member west of the session would be told the day
 * before the one on the schedule.
 */
final class MomentLine {

    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm",
            Locale.ENGLISH);
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm",
            Locale.ENGLISH);
    private static final DateTimeFormatter COMPACT_DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    /**
     * {@code ZoneId.of("UTC")}, never {@code ZoneOffset.UTC}, whose id is the single character {@code Z}: this zone is
     * rendered by name, and "16:00 in Z" is not a place.
     */
    private static final ZoneId UTC = ZoneId.of("UTC");

    static final String READER_ZONE_FACT = "readerTimeZone";
    static final String LABEL_FACT = "scheduledAtLabel";

    private MomentLine() {
    }

    /** The long form the mail templates render, under the verb each of them supplies. */
    static String full(Map<String, Object> facts) {

        return line(facts, FULL, FULL_DAY);
    }

    /** The short form a chat card and the in-app panel render, which is the shape those surfaces have always had. */
    static String compact(Map<String, Object> facts) {

        return line(facts, COMPACT, COMPACT_DAY);
    }

    private static String line(Map<String, Object> facts, DateTimeFormatter timed, DateTimeFormatter dayOnly) {

        if (!(facts.get("scheduledAt") instanceof Instant at)) {
            return null;
        }
        var sessionZone = sessionZoneOf(facts);
        // Boolean.FALSE.equals rather than a truth test: a fact map with no hasTime at all - a row stored before the
        // key existed - keeps the timed line it has always had.
        if (Boolean.FALSE.equals(facts.get("hasTime"))) {
            return dayOnly.withZone(sessionZone).format(at);
        }
        var readerZone = readerZoneOr(facts, sessionZone);
        var head = timed.withZone(readerZone).format(at) + " in " + cityOf(readerZone);
        if (sessionZone.getRules().getOffset(at).equals(readerZone.getRules().getOffset(at))) {
            return head;
        }
        return head + " (" + HOUR.withZone(sessionZone).format(at) + " in " + cityOf(sessionZone) + ")";
    }

    /**
     * The session's own clock: its series' zone for an occurrence, the zone it was typed in for a one-off. UTC only for
     * a row stored before a one-off carried a zone at all, which is the behaviour that row already had.
     */
    private static ZoneId sessionZoneOf(Map<String, Object> facts) {

        return facts.get("timeZone") instanceof String zone ? ZoneId.of(zone) : UTC;
    }

    private static ZoneId readerZoneOr(Map<String, Object> facts, ZoneId fallback) {

        return facts.get(READER_ZONE_FACT) instanceof String zone ? ZoneId.of(zone) : fallback;
    }

    /**
     * A zone named for a reader rather than for a database. "America/Los_Angeles" is an identifier; "Los Angeles" is
     * where somebody is.
     */
    private static String cityOf(ZoneId zone) {

        var id = zone.getId();
        return id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
    }
}
