package dev.axonia.events.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.SessionStatus;
import lombok.experimental.UtilityClass;

/**
 * One community's schedule as an RFC 5545 document: one concrete VEVENT per entry, real or projected, over the window
 * the caller asked for.
 *
 * <p>
 * Deliberately not an RRULE. {@code Cadence} is documented as a hint rather than an authoritative recurrence rule, so
 * emitting one would assert a certainty the domain refuses to have, and a rule cannot carry what makes an occurrence
 * worth attending: its own title, its own slots, its own room.
 *
 * <p>
 * The UID is the load-bearing part. {@code occurrenceDate} is immutable and a rescheduled session keeps its anchor, so
 * a series entry is identified by that anchor rather than by a session id - which means a projected occurrence keeps
 * the SAME UID once it materialises into a real row, and the subscriber's calendar updates the entry in place instead
 * of growing a duplicate beside it.
 *
 * <p>
 * {@code stamp} is the instant every VEVENT reports as its DTSTAMP, handed in rather than read from a clock here: this
 * class stays pure, one fetch stamps every event of its document identically, and the whole document becomes assertable
 * byte for byte. What that stamp should be is {@link #stampFor}'s answer, and it is not the wall clock.
 */
@UtilityClass
public class ScheduleIcsDocument {

    /**
     * Shorter than the 90-minute fallback the join window uses, and for the opposite reason: over-reserving somebody's
     * real calendar is worse than under-reserving it, while a join window erring long is harmless.
     */
    private static final int FALLBACK_MINUTES = 60;

    /**
     * The instant every VEVENT of one document should report as its DTSTAMP: the newest revision among the entries that
     * document will actually emit, never the moment of the fetch.
     *
     * <p>
     * This is the whole reason the feed's {@code ETag} can ever match. A wall-clock stamp changes every second, so two
     * polls a second apart hashed to two different validators, no {@code If-None-Match} could match, and the entire
     * 30-to-180-day schedule was re-sent on every unattended poll of every subscribed client, forever. Derived from
     * content instead, a poll that finds nothing changed re-renders the same bytes and costs one bodiless 304.
     *
     * <p>
     * {@code fallback} answers only for an entry that carries no revision of its own. Nothing this domain projects is
     * of that shape - a real session stamps {@code updatedAt} and a ghost reports its series' - and a window with no
     * entries at all emits no VEVENT, so no DTSTAMP is written and the document is byte-stable whatever the fallback
     * was.
     */
    public Instant stampFor(List<ScheduleEntry> entries, Instant fallback) {

        return emitted(entries).map(ScheduleEntry::updatedAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .orElse(fallback);
    }

    public String render(IcsDocumentRequest request) {

        var out = new StringBuilder();
        out.append(IcsWriter.line("BEGIN", "VCALENDAR"));
        out.append(IcsWriter.line("VERSION", "2.0"));
        out.append(IcsWriter.line("PRODID", "-//axonia//Community Schedule//EN"));
        out.append(IcsWriter.line("CALSCALE", "GREGORIAN"));
        out.append(IcsWriter.line("METHOD", "PUBLISH"));
        out.append(IcsWriter.line("NAME", IcsWriter.escapeText(request.calendarName())));
        out.append(IcsWriter.line("X-WR-CALNAME", IcsWriter.escapeText(request.calendarName())));
        out.append(IcsWriter.line("REFRESH-INTERVAL;VALUE=DURATION", "PT1H"));
        out.append(IcsWriter.line("X-PUBLISHED-TTL", "PT1H"));
        emitted(request.entries()).forEach(entry -> appendEvent(out, request, entry));
        out.append(IcsWriter.line("END", "VCALENDAR"));
        return out.toString();
    }

    /**
     * The entries this document publishes, which is what {@link #stampFor} has to stamp: a skipped occurrence is left
     * out altogether, so its revision is not part of what a subscriber can observe.
     */
    private Stream<ScheduleEntry> emitted(List<ScheduleEntry> entries) {

        return entries.stream().filter(entry -> entry.status() != SessionStatus.SKIPPED);
    }

    private void appendEvent(StringBuilder out, IcsDocumentRequest request, ScheduleEntry entry) {

        var minutes = entry.durationMinutes() != null ? entry.durationMinutes() : FALLBACK_MINUTES;
        out.append(IcsWriter.line("BEGIN", "VEVENT"));
        out.append(IcsWriter.line("UID", uidOf(entry, request.host())));
        out.append(IcsWriter.line("DTSTAMP", IcsWriter.utc(request.stamp())));
        appendWhen(out, entry, minutes);
        out.append(IcsWriter.line("SUMMARY", IcsWriter.escapeText(titleOf(entry))));
        out.append(IcsWriter.line("STATUS", entry.status() == SessionStatus.CANCELLED ? "CANCELLED" : "CONFIRMED"));
        out.append(IcsWriter.line("SEQUENCE", String.valueOf(entry.version() != null ? entry.version() : 0L)));
        if (entry.updatedAt() != null) {
            out.append(IcsWriter.line("LAST-MODIFIED", IcsWriter.utc(entry.updatedAt())));
        }
        appendPlace(out, entry, request.sessionBaseUrl());
        out.append(IcsWriter.line("END", "VEVENT"));
    }

    /**
     * An occurrence somebody chose an hour for is published as that hour. One nobody did is published as an all-day
     * entry, because the midnight its instant carries is a placeholder rather than a time, and a subscriber's calendar
     * has no way to tell the two apart from a DTSTART alone.
     *
     * <p>
     * The date is the day the instant falls on in the series' own zone, never the day it falls on in UTC: those are
     * different dates for every zone east of Greenwich. Nor is it {@code occurrenceDate}, which is the occurrence's
     * anchor in its series' cadence and stays where it was when an organizer moves the occurrence off it - reading it
     * as a day to publish strands every rescheduled untimed occurrence on the date it left. For an occurrence nobody
     * has moved the two answers are the same one.
     *
     * <p>
     * The all-day DTEND is exclusive, per RFC 5545: a one-day entry ends on the following date.
     *
     * <p>
     * Untimed with no series zone is stated as an invariant rather than left to a dereference. Only an occurrence of a
     * series can be untimed and every one of those carries its zone, so the case is a contradiction - but
     * {@code hasTime} is a primitive that a builder site forgetting the field defaults to {@code false}, and this is
     * the public unauthenticated feed, where the difference between a named contract and a raw
     * {@code NullPointerException} is the difference between a diagnosable bug and a 500 nobody can read.
     */
    private void appendWhen(StringBuilder out, ScheduleEntry entry, int minutes) {

        if (entry.hasTime()) {
            out.append(IcsWriter.line("DTSTART", IcsWriter.utc(entry.scheduledAt())));
            out.append(IcsWriter.line("DTEND", IcsWriter.utc(entry.scheduledAt().plus(Duration.ofMinutes(minutes)))));
            return;
        }
        var zone = entry.seriesTimeZone();
        if (zone == null) {
            throw new IllegalStateException(
                    "An occurrence with no time must belong to a series, because only a series composes an untimed "
                            + "occurrence: session " + entry.sessionId() + ", series " + entry.seriesId());
        }
        var day = OccurrenceInstant.anchorOf(entry.scheduledAt(), ZoneId.of(zone));
        out.append(IcsWriter.line("DTSTART;VALUE=DATE", IcsWriter.date(day)));
        out.append(IcsWriter.line("DTEND;VALUE=DATE", IcsWriter.date(day.plusDays(1))));
    }

    /**
     * The place, as the three lines a calendar client understands.
     *
     * <p>
     * {@code LOCATION} prefers the physical location and falls back to the join link when there is none, which is what
     * keeps every remote-only series byte-identical across the split that introduced this method: before it, the link
     * was the only place there was and it went here.
     *
     * <p>
     * {@code DESCRIPTION} deliberately never carries {@code joiningDetails}. A feed address is an unauthenticated
     * bearer token that lives in a subscriber's calendar store indefinitely, so a passcode published here outlives
     * every reset the product can perform. It carries a pointer to the session page instead, which is behind sign-in
     * and can be revoked, so the entry still leads somewhere.
     */
    private void appendPlace(StringBuilder out, ScheduleEntry entry, String sessionBaseUrl) {

        var place = entry.place() == null ? MeetingPlace.NONE : entry.place();
        if (place.isEmpty()) {
            return;
        }
        var located = place.location() != null ? place.location() : place.joinUrl();
        if (located != null) {
            out.append(IcsWriter.line("LOCATION", IcsWriter.escapeText(located)));
        }
        if (place.joinUrl() != null) {
            out.append(IcsWriter.line("CONFERENCE;VALUE=URI;FEATURE=VIDEO,AUDIO;LABEL=Join", place.joinUrl()));
        }
        if (place.joiningDetails() != null && entry.sessionId() != null) {
            out.append(IcsWriter.line("DESCRIPTION", IcsWriter
                    .escapeText("Joining details on the session page\n" + sessionBaseUrl + entry.sessionId())));
        }
    }

    /**
     * An anchored occurrence is named by its anchor so the id survives materialisation; a one-off, which has no anchor
     * by definition, is named by its row.
     */
    private String uidOf(ScheduleEntry entry, String host) {

        return entry.occurrenceDate() != null
                ? "series-" + entry.seriesId() + "-" + entry.occurrenceDate() + "@" + host
                : "session-" + entry.sessionId() + "@" + host;
    }

    private String titleOf(ScheduleEntry entry) {

        return entry.title() != null && !entry.title().isBlank() ? entry.title() : "Session";
    }
}
