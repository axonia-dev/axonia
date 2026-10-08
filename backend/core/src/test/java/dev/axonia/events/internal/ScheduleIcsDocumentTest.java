package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.axonia.events.MeetingPlace;
import dev.axonia.events.ScheduleEntry;
import dev.axonia.events.SessionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleIcsDocumentTest {

    private static final Instant STAMP = Instant.parse("2026-08-15T10:15:00Z");
    private static final String SESSION_BASE_URL = "https://axonia.example/communities/dojo/sessions/";

    private static ScheduleEntry ghost() {

        return ScheduleEntry.builder().seriesId(12L).title("Frontend Dojo").occurrenceDate(LocalDate.of(2026, 8, 18))
                .scheduledAt(Instant.parse("2026-08-18T16:00:00Z")).hasTime(true).durationMinutes(60)
                .status(SessionStatus.SCHEDULED).virtual(true)
                .place(new MeetingPlace(null, "https://meet.example/dojo", null)).build();
    }

    private static ScheduleEntry.ScheduleEntryBuilder entryBuilder() {

        return ScheduleEntry.builder().sessionId(1L).title("t").scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                .hasTime(true).durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(false);
    }

    private static String render(ScheduleEntry entry) {

        return render("d", List.of(entry));
    }

    private static String render(String calendarName, List<ScheduleEntry> entries) {

        return ScheduleIcsDocument.render(IcsDocumentRequest.builder().calendarName(calendarName).host("axonia.example")
                .sessionBaseUrl(SESSION_BASE_URL).stamp(STAMP).entries(entries).build());
    }

    /** RFC 5545 unfolding, which is what every consumer does before reading a property back. */
    private static String unfolded(String ics) {

        return ics.replace("\r\n ", "");
    }

    @Test
    void wrapsEveryEventInOneCalendar() {

        var ics = render("Frontend Dojo", List.of(ghost()));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("PRODID:-//axonia//Community Schedule//EN\r\n");
    }

    @Test
    void identifiesASeriesOccurrenceByItsAnchorSoItSurvivesMaterialisation() {

        var ics = render("Frontend Dojo", List.of(ghost()));

        assertThat(ics).contains("UID:series-12-2026-08-18@axonia.example\r\n");
    }

    @Test
    void keepsTheSameUidOnceTheGhostBecomesARealSession() {

        var real = ScheduleEntry.builder().sessionId(4471L).seriesId(12L).title("Frontend Dojo")
                .occurrenceDate(LocalDate.of(2026, 8, 18)).scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                .hasTime(true).durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(false)
                .place(new MeetingPlace(null, "https://meet.example/dojo", null)).build();

        assertThat(render("d", List.of(real))).contains("UID:series-12-2026-08-18@axonia.example\r\n");
    }

    @Test
    void identifiesAOneOffByItsSessionId() {

        var oneOff = ScheduleEntry.builder().sessionId(9001L).title("Architecture review")
                .scheduledAt(Instant.parse("2026-09-07T13:00:00Z")).hasTime(true).durationMinutes(45)
                .status(SessionStatus.SCHEDULED).virtual(false).build();

        assertThat(render(oneOff)).contains("UID:session-9001@axonia.example\r\n");
    }

    @Test
    void endsAtTheSessionsOwnLengthAndFallsBackToSixtyMinutes() {

        var noLength = ScheduleEntry.builder().sessionId(1L).title("t")
                .scheduledAt(Instant.parse("2026-08-18T16:00:00Z")).hasTime(true).status(SessionStatus.SCHEDULED)
                .virtual(false).build();

        assertThat(render("d", List.of(ghost()))).contains("DTEND:20260818T170000Z\r\n");
        assertThat(render(noLength)).contains("DTEND:20260818T170000Z\r\n");
    }

    @Test
    @DisplayName("an untimed occurrence publishes as an all-day entry, not a fabricated hour")
    void untimedIsAllDay() {

        var entry = entryBuilder().hasTime(false).seriesTimeZone("Europe/Zurich")
                .scheduledAt(Instant.parse("2026-08-12T22:00:00Z")).occurrenceDate(LocalDate.of(2026, 8, 13)).build();

        var ics = render(entry);

        assertThat(ics).contains("DTSTART;VALUE=DATE:20260813");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260814");
        assertThat(ics).doesNotContain("DTSTART:2026");
    }

    @Test
    @DisplayName("a timed occurrence is unchanged")
    void timedIsUnchanged() {

        var entry = entryBuilder().hasTime(true).scheduledAt(Instant.parse("2026-08-13T15:00:00Z")).build();

        assertThat(render(entry)).contains("DTSTART:20260813T150000Z");
    }

    /**
     * Only an occurrence of a series can be untimed, and every one of those carries its anchor, so an untimed entry
     * without an {@code occurrenceDate} is a contradiction rather than a case to handle. It fails loudly here instead
     * of being papered over by a guard that would have to invent a date nobody chose.
     *
     * <p>
     * The type and the message are both pinned, and that is the point of this test rather than a detail of it.
     * {@code hasTime} is a primitive, so a future builder site that simply forgets the field gets {@code false} for
     * free, and this document is rendered for the public unauthenticated feed: an unnamed {@code NullPointerException}
     * there is a 500 whose cause nobody reading the log can place.
     */
    @Test
    @DisplayName("an untimed entry belonging to no series is a contradiction, and names it")
    void untimedWithoutASeriesZoneThrows() {

        var entry = entryBuilder().hasTime(false).seriesTimeZone(null).build();

        assertThatThrownBy(() -> render(entry)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must belong to a series").hasMessageContaining("session 1");
    }

    @Test
    @DisplayName("a rescheduled untimed occurrence publishes the day it moved to, not its anchor")
    void untimedFollowsAReschedule() {

        var moved = entryBuilder().hasTime(false).seriesTimeZone("Europe/Zurich")
                .scheduledAt(Instant.parse("2026-08-14T22:00:00Z")).occurrenceDate(LocalDate.of(2026, 8, 13)).build();

        var ics = render(moved);

        assertThat(ics).contains("DTSTART;VALUE=DATE:20260815");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260816");
    }

    @Test
    void carriesTheRoomAsBothLocationAndConference() {

        var ics = render("d", List.of(ghost()));

        assertThat(ics).contains("LOCATION:https://meet.example/dojo\r\n");
        assertThat(unfolded(ics))
                .contains("CONFERENCE;VALUE=URI;FEATURE=VIDEO,AUDIO;LABEL=Join:https://meet.example/dojo\r\n");
    }

    /**
     * The property that makes the room clickable is URI-typed, so a comma stays a comma. Escaping it the way LOCATION
     * is escaped produces a link no client can follow, and the two lines sit beside each other in the writer for
     * exactly this reason.
     */
    @Test
    void escapesTheRoomForLocationAndLeavesItLiteralForConference() {

        var comma = entryBuilder().place(new MeetingPlace(null, "https://meet.example/a,b", null)).build();

        var ics = unfolded(render(comma));

        assertThat(ics).contains("LOCATION:https://meet.example/a\\,b\r\n");
        assertThat(ics).contains("LABEL=Join:https://meet.example/a,b\r\n");
    }

    @Test
    @DisplayName("LOCATION carries the physical place and CONFERENCE carries the link")
    void locationAndConference() {

        var entry = entryBuilder().place(new MeetingPlace("Room 4B, Gasperich", "https://teams.microsoft.com/l/abc",
                "ID 391 2244, passcode 4471")).build();

        var ics = render(entry);

        assertThat(ics).contains("LOCATION:Room 4B\\, Gasperich");
        assertThat(ics).contains("CONFERENCE;VALUE=URI;FEATURE=VIDEO,AUDIO;LABEL=Join:");
    }

    @Test
    @DisplayName("a remote-only session still puts its link in LOCATION, as it did before the split")
    void locationFallsBackToTheLink() {

        var entry = entryBuilder().place(new MeetingPlace(null, "https://teams.microsoft.com/l/abc", null)).build();

        assertThat(render(entry)).contains("LOCATION:https://teams.microsoft.com/l/abc");
    }

    /**
     * The feed's own half of the CRLF-injection defence, asserted on the document rather than on the writer.
     * {@code CONFERENCE} is URI-typed and therefore deliberately NOT escaped, so before the writer stripped control
     * characters a stored join link carrying a CRLF wrote whatever followed it as further calendar properties into a
     * document nobody authenticates to read. The value is planted straight onto the record, past the validation that
     * now refuses it, because this end has to hold on its own.
     */
    @Test
    @DisplayName("a control character in the join link cannot write a property of its own into the feed")
    void anInjectedPropertyInTheJoinLinkNeverBecomesALine() {

        var entry = entryBuilder().place(new MeetingPlace(null, "https://x.example/a\r\nATTENDEE:mailto:a@b.c", null))
                .build();

        var ics = render(entry);

        assertThat(ics.lines().filter(line -> line.startsWith("ATTENDEE"))).isEmpty();
        assertThat(unfolded(ics)).contains("CONFERENCE;VALUE=URI;FEATURE=VIDEO,AUDIO;LABEL=Join:"
                + "https://x.example/aATTENDEE:mailto:a@b.c\r\n");
    }

    @Test
    @DisplayName("the joining details never reach the feed, only a pointer to the session page")
    void joiningDetailsAreWithheld() {

        var entry = entryBuilder().sessionId(1182L)
                .place(new MeetingPlace(null, "https://teams.microsoft.com/l/abc", "ID 391 2244, passcode 4471"))
                .build();

        var ics = render(entry);

        assertThat(ics).doesNotContain("391 2244");
        assertThat(ics).doesNotContain("passcode");
        assertThat(unfolded(ics)).contains(
                "DESCRIPTION:Joining details on the session page\\nhttps://axonia.example/communities/dojo/sessions/1182\r\n");
    }

    /**
     * The other half of the {@code sessionId != null} guard: a ghost is a projected occurrence with no row of its own
     * yet, so it has no session page a pointer could lead to. Emitting one anyway would read {@code .../sessions/null}
     * to whoever opened it.
     */
    @Test
    @DisplayName("a ghost has no session page yet, so joining details never grow a description for it")
    void ghostEmitsNoDescriptionEvenWithJoiningDetails() {

        var entry = ScheduleEntry.builder().seriesId(12L).title("Frontend Dojo")
                .occurrenceDate(LocalDate.of(2026, 8, 18)).scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                .hasTime(true).durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(true)
                .place(new MeetingPlace(null, "https://teams.microsoft.com/l/abc", "ID 391 2244, passcode 4471"))
                .build();

        assertThat(render(entry)).doesNotContain("DESCRIPTION");
    }

    @Test
    @DisplayName("joining details with neither a location nor a link still points at the session page, alone")
    void joiningDetailsAloneEmitNoLocationOrConference() {

        var entry = entryBuilder().sessionId(77L).place(new MeetingPlace(null, null, "ID 391 2244, passcode 4471"))
                .build();

        var ics = render(entry);

        assertThat(ics).doesNotContain("LOCATION");
        assertThat(ics).doesNotContain("CONFERENCE");
        assertThat(unfolded(ics)).contains(
                "DESCRIPTION:Joining details on the session page\\nhttps://axonia.example/communities/dojo/sessions/77\r\n");
    }

    @Test
    @DisplayName("a session with no place at all emits no place lines and no description")
    void noPlaceEmitsNothing() {

        var ics = render(entryBuilder().place(MeetingPlace.NONE).build());

        assertThat(ics).doesNotContain("LOCATION");
        assertThat(ics).doesNotContain("CONFERENCE");
        assertThat(ics).doesNotContain("DESCRIPTION");
    }

    @Test
    void omitsBothPropertiesEntirelyWhenNoRoomIsSet() {

        var roomless = ScheduleEntry.builder().sessionId(1L).title("t")
                .scheduledAt(Instant.parse("2026-08-18T16:00:00Z")).hasTime(true).durationMinutes(60)
                .status(SessionStatus.SCHEDULED).virtual(false).build();

        var ics = render(roomless);

        assertThat(ics).doesNotContain("LOCATION").doesNotContain("CONFERENCE");
    }

    @Test
    void saysACancelledSessionIsOffAndOmitsASkippedOneAltogether() {

        var cancelled = ScheduleEntry.builder().sessionId(1L).title("t")
                .scheduledAt(Instant.parse("2026-08-18T16:00:00Z")).hasTime(true).durationMinutes(60)
                .status(SessionStatus.CANCELLED).virtual(false).build();
        var skipped = ScheduleEntry.builder().sessionId(2L).title("t")
                .scheduledAt(Instant.parse("2026-08-25T16:00:00Z")).hasTime(true).durationMinutes(60)
                .status(SessionStatus.SKIPPED).virtual(false).build();

        var ics = render("d", List.of(cancelled, skipped));

        assertThat(ics).contains("STATUS:CANCELLED\r\n");
        assertThat(ics).contains("UID:session-1@axonia.example\r\n").doesNotContain("UID:session-2@axonia.example");
    }

    @Test
    void namesAnUntitledOccurrenceRatherThanEmittingABlankSummary() {

        var untitled = ScheduleEntry.builder().sessionId(1L).title("  ")
                .scheduledAt(Instant.parse("2026-08-18T16:00:00Z")).hasTime(true).durationMinutes(60)
                .status(SessionStatus.SCHEDULED).virtual(false).build();

        assertThat(render(untitled)).contains("SUMMARY:Session\r\n");
    }

    /**
     * The stamp a feed should carry: the newest revision it publishes, so the same schedule renders the same bytes
     * however long after the last change it is fetched.
     */
    @Test
    void stampsADocumentWithTheNewestRevisionItPublishes() {

        var older = ScheduleEntry.builder().sessionId(1L).title("t").scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                .hasTime(true).status(SessionStatus.SCHEDULED).virtual(false)
                .updatedAt(Instant.parse("2026-08-10T08:00:00Z")).build();
        var newer = ScheduleEntry.builder().sessionId(2L).title("t").scheduledAt(Instant.parse("2026-08-25T16:00:00Z"))
                .hasTime(true).status(SessionStatus.SCHEDULED).virtual(false)
                .updatedAt(Instant.parse("2026-08-14T09:00:12Z")).build();

        assertThat(ScheduleIcsDocument.stampFor(List.of(older, newer), STAMP))
                .isEqualTo(Instant.parse("2026-08-14T09:00:12Z"));
    }

    /** A skipped occurrence is never published, so a revision to it is not something a subscriber can observe. */
    @Test
    void ignoresTheRevisionOfAnOccurrenceItLeavesOut() {

        var published = ScheduleEntry.builder().sessionId(1L).title("t")
                .scheduledAt(Instant.parse("2026-08-18T16:00:00Z")).hasTime(true).status(SessionStatus.SCHEDULED)
                .virtual(false).updatedAt(Instant.parse("2026-08-10T08:00:00Z")).build();
        var skipped = ScheduleEntry.builder().sessionId(2L).title("t")
                .scheduledAt(Instant.parse("2026-08-25T16:00:00Z")).hasTime(true).status(SessionStatus.SKIPPED)
                .virtual(false).updatedAt(Instant.parse("2026-08-14T09:00:12Z")).build();

        assertThat(ScheduleIcsDocument.stampFor(List.of(published, skipped), STAMP))
                .isEqualTo(Instant.parse("2026-08-10T08:00:00Z"));
    }

    /**
     * An empty window writes no VEVENT and therefore no DTSTAMP at all, so the fallback is unobservable there; it
     * exists so the stamp is never null for an entry that reports no revision of its own.
     */
    @Test
    void fallsBackToTheSuppliedStampWhenNothingReportsARevision() {

        assertThat(ScheduleIcsDocument.stampFor(List.of(ghost()), STAMP)).isEqualTo(STAMP);
        assertThat(ScheduleIcsDocument.stampFor(List.of(), STAMP)).isEqualTo(STAMP);
    }

    /**
     * The whole document, byte for byte, which only a caller-supplied DTSTAMP makes assertable. The last two lines of
     * the CONFERENCE property are folded because it crosses 75 octets, which is what a real feed does and what no
     * {@code contains} assertion can prove.
     */
    @Test
    void rendersOneWholeDocumentByteForByte() {

        var entry = ScheduleEntry.builder().seriesId(12L).title("Frontend Dojo")
                .occurrenceDate(LocalDate.of(2026, 8, 18)).scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                .hasTime(true).durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(true)
                .place(new MeetingPlace(null, "https://meet.example/dojo", null)).version(3L)
                .updatedAt(Instant.parse("2026-08-14T09:00:12Z")).build();

        var ics = render("Frontend Dojo", List.of(entry));

        assertThat(ics).isEqualTo("""
                BEGIN:VCALENDAR\r
                VERSION:2.0\r
                PRODID:-//axonia//Community Schedule//EN\r
                CALSCALE:GREGORIAN\r
                METHOD:PUBLISH\r
                NAME:Frontend Dojo\r
                X-WR-CALNAME:Frontend Dojo\r
                REFRESH-INTERVAL;VALUE=DURATION:PT1H\r
                X-PUBLISHED-TTL:PT1H\r
                BEGIN:VEVENT\r
                UID:series-12-2026-08-18@axonia.example\r
                DTSTAMP:20260815T101500Z\r
                DTSTART:20260818T160000Z\r
                DTEND:20260818T170000Z\r
                SUMMARY:Frontend Dojo\r
                STATUS:CONFIRMED\r
                SEQUENCE:3\r
                LAST-MODIFIED:20260814T090012Z\r
                LOCATION:https://meet.example/dojo\r
                CONFERENCE;VALUE=URI;FEATURE=VIDEO,AUDIO;LABEL=Join:https://meet.example/do\r
                 jo\r
                END:VEVENT\r
                END:VCALENDAR\r
                """);
    }
}
