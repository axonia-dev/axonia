package dev.axonia.roundup.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;

/**
 * One closed calendar quarter, the period a roundup is always about. {@link #key()} - {@code "2026-Q2"} - is also the
 * outbox subject reference, which is what makes the outbox's own {@code (organization_id, message_type, subject_ref)}
 * unique key say "this organization has already been told about this quarter" without this module tracking anything of
 * its own.
 *
 * <p>
 * <b>UTC throughout, deliberately, and not the reader's zone.</b> A roundup is an organization-wide artefact with no
 * single local time: it is assembled once for a whole tenant and lands in inboxes spread across as many zones as the
 * tenant has offices. Picking a tenant's zone - or a reader's - would make the same quarter mean different things to
 * different readers of the same message, and would move the boundary a session sits on depending on who opened the
 * mail. One fixed offset makes "2026-Q2" the same three months for everybody it reaches.
 *
 * <p>
 * The window is half-open, {@code [start, endExclusive)}, matching how {@code SessionActivity} and
 * {@code ArtifactActivity} declare theirs: the first instant of the following quarter belongs to that quarter, never to
 * this one.
 */
public record Quarter(int year, int index) {

    private static final int QUARTERS_PER_YEAR = 4;

    /**
     * The quarter that had already ended at {@code now}. On the first instant of a quarter that is the quarter just
     * gone; a nanosecond before it, the one before that.
     */
    public static Quarter mostRecentlyClosed(Instant now) {

        var closed = now.atZone(ZoneOffset.UTC).with(IsoFields.DAY_OF_QUARTER, 1).truncatedTo(ChronoUnit.DAYS)
                .minusMonths(3);
        return new Quarter(closed.getYear(), closed.get(IsoFields.QUARTER_OF_YEAR));
    }

    /**
     * The quarter that follows this one - the window "what is coming" looks into. Derived from the reported quarter
     * rather than from {@code now} on purpose: it makes the whole assembly a pure function of the quarter, so the
     * preview's live-facts fallback cannot list a different set of sessions from the send it stands in for.
     */
    public Quarter next() {

        return index == QUARTERS_PER_YEAR ? new Quarter(year + 1, 1) : new Quarter(year, index + 1);
    }

    public String key() {

        return year + "-Q" + index;
    }

    public Instant start() {

        return firstDay().toInstant();
    }

    public Instant endExclusive() {

        return firstDay().plusMonths(3).toInstant();
    }

    private ZonedDateTime firstDay() {

        return ZonedDateTime.of(year, index * 3 - 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    }
}
