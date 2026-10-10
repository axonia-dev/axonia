package dev.axonia.communication.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import dev.axonia.communication.application.FactRehydration;
import dev.axonia.communication.application.MessageRenderAdapter;
import dev.axonia.communication.application.NotificationEntryBuilders;
import dev.axonia.communication.application.port.out.NotificationCentreReads;
import dev.axonia.communication.domain.NotificationEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The panel's own reads and writes: which rows are visible (sent, in-app, this member's own), the newest-first keyset
 * order, the {@code hasMore} signal, the two marks, and the unread count.
 * {@link #theLineIsRenderedFromARealJsonRoundTrip} pins that {@code scheduledAt} must be rehydrated from the jsonb
 * round trip's ISO string back into a real {@code Instant} through {@link FactRehydration}, not read raw - a raw read
 * would silently render every session entry with a null line, and no in-memory-built facts map would ever catch it.
 */
class JdbcNotificationCentreTest extends CommunicationJdbcTestSupport {

    private static final Instant SENT_AT = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-14T13:00:00Z");

    @Autowired
    private NotificationCentreReads centre;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeTransaction
    void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void thePanelShowsOnlySentInAppRowsForThatMember() {

        var messageId = insertMessage("601");
        insertDelivery(messageId, "alice", "IN_APP", "SENT", SENT_AT);
        insertDelivery(messageId, "alice", "EMAIL", "SENT", SENT_AT);
        insertDelivery(messageId, "bob", "IN_APP", "SENT", SENT_AT);

        var page = centre.page("alice", null, null, 20);

        assertThat(page.entries()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void aPendingInAppRowIsNotVisibleYet() {

        var messageId = insertMessage("602");
        insertDelivery(messageId, "alice", "IN_APP", "PENDING", null);

        assertThat(centre.page("alice", null, null, 20).entries()).isEmpty();
    }

    @Test
    void entriesComeBackNewestFirst() {

        insertSentEntry("603", "alice", Instant.parse("2026-08-01T10:00:00Z"));
        insertSentEntry("604", "alice", Instant.parse("2026-08-03T10:00:00Z"));

        var page = centre.page("alice", null, null, 20);

        assertThat(page.entries()).extracting(NotificationEntry::sentAt)
                .containsExactly(Instant.parse("2026-08-03T10:00:00Z"), Instant.parse("2026-08-01T10:00:00Z"));
    }

    @Test
    void aFullPageReportsThatMoreRemain() {

        insertSentEntry("605", "alice", Instant.parse("2026-08-01T10:00:00Z"));
        insertSentEntry("606", "alice", Instant.parse("2026-08-02T10:00:00Z"));

        var page = centre.page("alice", null, null, 1);

        assertThat(page.entries()).hasSize(1);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void theCursorReturnsEachEntryExactlyOnceAcrossPages() {

        insertSentEntry("607", "alice", Instant.parse("2026-08-01T10:00:00Z"));
        insertSentEntry("608", "alice", Instant.parse("2026-08-02T10:00:00Z"));
        insertSentEntry("609", "alice", Instant.parse("2026-08-03T10:00:00Z"));

        var first = centre.page("alice", null, null, 2);
        var last = first.entries().get(1);
        var second = centre.page("alice", last.sentAt(), last.id(), 2);

        assertThat(second.entries()).hasSize(1);
        assertThat(second.entries().get(0).id()).isNotIn(first.entries().stream().map(NotificationEntry::id).toList());
    }

    @Test
    void twoEntriesSharingOneInstantAreStillPagedWithoutRepeating() {

        var shared = Instant.parse("2026-08-01T10:00:00Z");
        insertSentEntry("610", "alice", shared);
        insertSentEntry("611", "alice", shared);

        var first = centre.page("alice", null, null, 1);
        var last = first.entries().get(0);
        var second = centre.page("alice", last.sentAt(), last.id(), 1);

        assertThat(second.entries().get(0).id()).isNotEqualTo(last.id());
    }

    @Test
    void theUnreadCountIgnoresReadEntriesAndOtherMembers() {

        insertSentEntry("612", "alice", SENT_AT);
        insertSentEntry("613", "alice", SENT_AT);
        insertSentEntry("614", "bob", SENT_AT);
        centre.markAllRead("alice", NOW);
        insertSentEntry("615", "alice", SENT_AT);

        assertThat(centre.unreadCount("alice")).isEqualTo(1);
        // Pins that markAllRead's own WHERE carries the user_id predicate: nothing would fail here if that predicate
        // were dropped and markAllRead ran only against alice's rows by coincidence of them being the only ones
        // touched above, since bob's own entry was never marked - a lost predicate would only surface once bob's
        // count is actually asserted.
        assertThat(centre.unreadCount("bob")).isEqualTo(1);
    }

    @Test
    void markingAnotherMembersEntryReadChangesNothing() {

        var id = insertSentEntry("616", "bob", SENT_AT);

        assertThat(centre.markRead("alice", id, NOW)).isZero();
        assertThat(centre.unreadCount("bob")).isEqualTo(1);
    }

    @Test
    void markingAnAlreadyReadEntryReadAgainIsANoOp() {

        var id = insertSentEntry("617", "alice", SENT_AT);
        centre.markRead("alice", id, NOW);

        assertThat(centre.markRead("alice", id, LATER)).isZero();
    }

    /**
     * {@code markRead}'s own {@code WHERE} once omitted {@code status = 'SENT'}, which let a member mark their own
     * still-{@code PENDING} in-app delivery read before the drain ever sent it: the row would then arrive already read,
     * never counted by the badge and never surfaced as unread, with no trace of what happened. {@code page},
     * {@code unreadCount} and {@code markAllRead} all already carried the constraint; {@code markRead} alone did not.
     */
    @Test
    void markingAPendingEntryReadChangesNothing() {

        var messageId = insertMessage("630");
        var id = insertDelivery(messageId, "alice", "IN_APP", "PENDING", null);

        assertThat(centre.markRead("alice", id, NOW)).isZero();
    }

    /**
     * A read entry is not hidden by {@code page} - only {@code unreadCount} and the badge care about {@code read_at}.
     * Also the one test in this class that renders an entry back out through {@code page} after marking it read, so
     * {@code read_at IS NOT NULL} - the {@code read} field's true arm - is exercised at least once, not just its false
     * arm every other test here leaves untouched.
     */
    @Test
    void aReadEntryStillAppearsInThePageMarkedRead() {

        var id = insertSentEntry("620", "alice", SENT_AT);
        centre.markRead("alice", id, NOW);

        var page = centre.page("alice", null, null, 20);

        assertThat(page.entries()).singleElement().satisfies(entry -> assertThat(entry.read()).isTrue());
    }

    /**
     * A row whose {@code message_type} this deploy no longer declares - a type retired since the row was sent - must
     * not fail the whole page, so a member whose panel holds one old row would otherwise see an empty panel or a 500
     * instead of everything else that is still theirs.
     */
    @Test
    void aRowWhoseMessageTypeThisDeployNoLongerDeclaresIsDropped() {

        var messageId = insertMessage("621", "legacy_reminder", "{}");
        insertDelivery(messageId, "alice", "IN_APP", "SENT", SENT_AT);

        assertThat(centre.page("alice", null, null, 20).entries()).isEmpty();
    }

    /**
     * {@code hasMore} must be read off the raw window before the drop, not after: with a page {@code size} of 1, the
     * newest of three rows is the dropped one, so the raw {@code LIMIT 2} fetch is {@code [dropped, second]}; the
     * window is truncated to the first row - the dropped one - which then filters down to zero visible entries. A
     * third, older row genuinely remains behind that window, unseen by this call. Computing {@code hasMore} on the
     * already-filtered list (as this class once did) would instead see one filtered row inside a size-1 window and
     * wrongly report {@code hasMore = false}, permanently stranding the older row: nothing about a client's next call
     * would ever ask for it again.
     */
    @Test
    void aFullPageWithADroppedRowStillReportsMoreRemain() {

        insertSentEntry("623", "alice", Instant.parse("2026-08-01T10:00:00Z"));
        insertSentEntry("624", "alice", Instant.parse("2026-08-02T10:00:00Z"));
        var messageId = insertMessage("625", "legacy_reminder", "{}");
        insertDelivery(messageId, "alice", "IN_APP", "SENT", Instant.parse("2026-08-03T10:00:00Z"));

        var page = centre.page("alice", null, null, 1);

        assertThat(page.entries()).isEmpty();
        assertThat(page.hasMore()).isTrue();
    }

    /**
     * Reporting that more remain is only half an answer if the caller cannot say where to resume from, and a page whose
     * every row was dropped leaves it nothing to derive one from: its last visible entry does not exist. The cursor is
     * therefore the server's to state, off the raw window, and this walks the whole way through a wholly-unreadable
     * page to the two readable entries stranded behind it. Derived client-side, paging stopped here and those two were
     * unreachable for as long as that one retired row stayed the newest thing in the panel.
     */
    @Test
    void aPageWhoseEveryRowWasDroppedStillSaysWhereToResumeFrom() {

        insertSentEntry("640", "alice", Instant.parse("2026-08-01T10:00:00Z"));
        insertSentEntry("641", "alice", Instant.parse("2026-08-02T10:00:00Z"));
        var messageId = insertMessage("642", "legacy_reminder", "{}");
        var droppedId = insertDelivery(messageId, "alice", "IN_APP", "SENT", Instant.parse("2026-08-03T10:00:00Z"));

        var first = centre.page("alice", null, null, 1);

        assertThat(first.entries()).isEmpty();
        assertThat(first.nextBefore()).isEqualTo(Instant.parse("2026-08-03T10:00:00Z"));
        assertThat(first.nextBeforeId()).isEqualTo(droppedId);

        var second = centre.page("alice", first.nextBefore(), first.nextBeforeId(), 20);

        assertThat(second.entries()).hasSize(2);
        assertThat(second.hasMore()).isFalse();
    }

    /**
     * The cursor is the last row of the window actually served, not of the one-row-wider read {@code hasMore} is
     * answered from. Serving the wider window's last row would skip whichever row sat between them, silently, on every
     * full page: the whole of the panel's second entry onwards.
     */
    @Test
    void theCursorIsTheLastRowServedNotTheExtraRowReadToAnswerHasMore() {

        var oldest = insertSentEntry("643", "alice", Instant.parse("2026-08-01T10:00:00Z"));
        var served = insertSentEntry("644", "alice", Instant.parse("2026-08-02T10:00:00Z"));
        insertSentEntry("645", "alice", Instant.parse("2026-08-03T10:00:00Z"));

        var first = centre.page("alice", null, null, 2);

        assertThat(first.nextBeforeId()).isEqualTo(served);

        var second = centre.page("alice", first.nextBefore(), first.nextBeforeId(), 20);

        assertThat(second.entries()).extracting(NotificationEntry::id).containsExactly(oldest);
    }

    /** A last page has nowhere to resume from, and must say so rather than hand back a cursor nothing sits behind. */
    @Test
    void aPageWithNothingBehindItCarriesNoCursor() {

        insertSentEntry("646", "alice", SENT_AT);

        var page = centre.page("alice", null, null, 20);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextBefore()).isNull();
        assertThat(page.nextBeforeId()).isNull();
    }

    /**
     * {@code before} without a matching {@code beforeId} used to default the missing half to {@code 0}, degrading the
     * tuple comparison to {@code (sent_at, id) < (before, 0)} - which excludes every row sharing that exact instant,
     * since a real id is never less than zero. A half cursor is now treated as no cursor at all, so both rows sharing
     * {@code shared} come back rather than neither.
     */
    @Test
    void aCursorWithOnlyBeforeIsTreatedAsNoCursor() {

        var shared = Instant.parse("2026-08-01T10:00:00Z");
        insertSentEntry("628", "alice", shared);
        insertSentEntry("629", "alice", shared);

        var page = centre.page("alice", shared, null, 20);

        assertThat(page.entries()).hasSize(2);
    }

    @Test
    void aNonPositiveSizeYieldsAnEmptyPageThatDoesNotClaimMore() {

        insertSentEntry("631", "alice", SENT_AT);

        var page = centre.page("alice", null, null, 0);

        assertThat(page.entries()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    /**
     * {@code scheduledAt} is inserted here exactly as a real send would leave it in the {@code facts} column - a jsonb
     * string, the ISO text an {@code Instant} serializes to - never a real {@code Instant} constructed in memory,
     * because that shape would pass even a raw {@code jsonMapper.readValue} and never expose the bug. A raw read leaves
     * {@code scheduledAt} a {@code String}; {@link NotificationEntryBuilders#build} 's {@code instanceof Instant} check
     * then silently fails, and {@code line} comes back {@code null} with no date, for every session entry, forever.
     * {@link JdbcNotificationCentre#toEntry} must instead rehydrate through {@link FactRehydration#parse} - the same
     * repair {@link MessageRenderAdapter#renderStoredRow} already applies to a stored row's facts - which is what turns
     * that string back into the real {@code Instant} the formatter needs.
     */
    @Test
    void theLineIsRenderedFromARealJsonRoundTrip() {

        var messageId = insertMessage("622", "session_wrap_up", "{\"scheduledAt\": \"2026-08-01T10:00:00Z\"}");
        insertDelivery(messageId, "alice", "IN_APP", "SENT", SENT_AT);

        var page = centre.page("alice", null, null, 20);

        assertThat(page.entries()).singleElement()
                .satisfies(entry -> assertThat(entry.line()).isEqualTo("Held 1 August 2026 at 10:00 in UTC"));
    }

    private Long insertMessage(String subjectRef) {

        return insertMessage(subjectRef, "session_wrap_up", "{}");
    }

    private Long insertMessage(String subjectRef, String messageType, String factsJson) {

        return jdbc.queryForObject("""
                INSERT INTO communication_message
                    (organization_id, message_type, subject_ref, template, facts, created_at, updated_at, version)
                VALUES ('acme', :type, :subject, 'session-wrap-up', CAST(:facts AS jsonb), :now, :now, 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("type", messageType).addValue("subject", subjectRef)
                .addValue("facts", factsJson).addValue("now", Timestamp.from(NOW)), Long.class);
    }

    private Long insertDelivery(Long messageId, String userId, String channel, String status, Instant sentAt) {

        return jdbc.queryForObject("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, channel, status, sent_at, created_at, updated_at, version)
                VALUES ('acme', :message, :user, :channel, :status, :sentAt, :now, :now, 0)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("message", messageId).addValue("user", userId)
                        .addValue("channel", channel).addValue("status", status)
                        .addValue("sentAt", sentAt == null ? null : Timestamp.from(sentAt))
                        .addValue("now", Timestamp.from(NOW)),
                Long.class);
    }

    /** {@code insertMessage} followed by an {@code IN_APP} delivery already {@code SENT} at the given instant. */
    private Long insertSentEntry(String subjectRef, String userId, Instant sentAt) {

        var messageId = insertMessage(subjectRef);
        return jdbc.queryForObject("""
                INSERT INTO communication_delivery
                    (organization_id, message_id, user_id, channel, status, sent_at, created_at, updated_at, version)
                VALUES ('acme', :message, :user, 'IN_APP', 'SENT', :sentAt, :now, :now, 0)
                RETURNING id
                """, new MapSqlParameterSource().addValue("message", messageId).addValue("user", userId)
                .addValue("sentAt", Timestamp.from(sentAt)).addValue("now", Timestamp.from(NOW)), Long.class);
    }
}
