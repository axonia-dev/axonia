package dev.axonia.communication.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.FactRehydration;
import dev.axonia.communication.application.MessageRenderAdapter;
import dev.axonia.communication.application.NotificationEntryBuilders;
import dev.axonia.communication.application.port.out.NotificationCentreReads;
import dev.axonia.communication.domain.NotificationEntry;
import dev.axonia.communication.domain.NotificationPage;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The panel's own reads and writes. Every statement carries the explicit {@code organization_id =
 * current_setting('app.current_org', true)} predicate this module layers over RLS as defence in depth, and every one of
 * them is scoped by {@code user_id} as well, so acting on somebody else's entry updates zero rows rather than erroring.
 * {@code page}'s join adds the identical predicate on {@code communication_message} too, not just
 * {@code communication_delivery}: {@code d}'s own predicate already prevents a cross-tenant row from being selected,
 * but the join condition itself should not silently trust that the message it resolves to belongs to the same tenant,
 * which is what {@code m.organization_id = d.organization_id} states outright. {@code markRead} and {@code markAllRead}
 * carry {@code status = 'SENT'} for the same reason {@code page} and {@code unreadCount} do: a member must not be able
 * to mark a still-{@code PENDING} row of their own read before it has ever reached their panel, which would silently
 * swallow the badge count and the entry both, the moment the drain finally sends it.
 *
 * <p>
 * Paging is a keyset cursor on {@code (sent_at, id)}, not an offset. The drain inserts rows at the head of this exact
 * list while a member is scrolling it, and an offset would skip or repeat entries as it did. Ordering cannot be by
 * {@code id} alone either: a reminder queued two days ahead of its send carries a lower id than a wrap-up queued and
 * sent after it. A cursor is honoured only when both {@code before} and {@code beforeId} are given - see {@code page}'s
 * own comment for why a half cursor is treated as no cursor rather than acted on literally. {@code hasMore} is computed
 * on the raw, untruncated window before an unrecognized-type row is filtered out, also explained on {@code page}
 * itself: filtering first would let one dropped row silently truncate a page that in fact had more behind it.
 *
 * <p>
 * The cursor the next page resumes from is read off that same raw window and returned as
 * {@link NotificationPage#nextBefore} / {@link NotificationPage#nextBeforeId} rather than left for the caller to derive
 * from the last entry it can see. A window whose every row was dropped has no visible last entry to derive one from, so
 * a caller doing that derivation stops paging there and strands every older readable entry behind it. Only this class
 * saw the rows the page was cut from, so only this class can state where it ends.
 *
 * <p>
 * Nothing here reads the clock. {@code markRead} and {@code markAllRead} take the instant from their caller, which
 * takes it from the injected {@code Clock}, because no timestamp in this codebase is stamped by the database.
 *
 * <p>
 * {@code toEntry} rehydrates a row's {@code facts} through {@link FactRehydration#parse(JsonMapper, String)} rather
 * than a raw {@code jsonMapper.readValue}: {@code scheduledAt} is written as a real {@code java.time.Instant} but
 * crosses a jsonb round trip and comes back out as the ISO string it was stored as, exactly the problem
 * {@link MessageRenderAdapter#renderStoredRow} already solved for a preview. Reading it raw here would make
 * {@link NotificationEntryBuilders}'s {@code instanceof Instant} check silently fail for every session entry, leaving
 * every one of them with a null line and no date.
 */
@Repository
@RequiredArgsConstructor
class JdbcNotificationCentre implements NotificationCentreReads {

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final NotificationEntryBuilders builders;

    @Override
    public NotificationPage page(String userId, Instant before, Long beforeId, int size) {

        if (size <= 0) {
            return new NotificationPage(List.of(), false, null, null);
        }

        // A cursor is both halves or neither: before without beforeId degrades the tuple comparison to
        // (sent_at, id) < (before, 0), which drops every row sharing that exact instant - precisely the tie the
        // keyset design exists to handle - and beforeId without before would silently ignore the cursor and re-serve
        // page 1, which a paging client turns into an infinite loop. Treating a half cursor as no cursor at all is
        // the only answer that cannot corrupt a page.
        var cursored = before != null && beforeId != null;

        var raw = jdbc.query("""
                SELECT d.id, d.sent_at, d.read_at, m.message_type, m.facts
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id AND m.organization_id = d.organization_id
                 WHERE d.organization_id = current_setting('app.current_org', true)
                   AND d.user_id = :user
                   AND d.channel = 'IN_APP'
                   AND d.status = 'SENT'
                   AND (CAST(:before AS timestamptz) IS NULL
                        OR (d.sent_at, d.id) < (CAST(:before AS timestamptz), :beforeId))
                 ORDER BY d.sent_at DESC, d.id DESC
                 LIMIT :limit
                """,
                new MapSqlParameterSource().addValue("user", userId)
                        .addValue("before", cursored ? Timestamp.from(before) : null)
                        .addValue("beforeId", cursored ? beforeId : 0L).addValue("limit", size + 1),
                this::toRow);

        // hasMore and the cursor are both read off the raw, untruncated window, before an unknown message type's row
        // is dropped: a row this deploy no longer declares is one row narrower than the client's page, not one row
        // emptier than the whole panel, and it still holds the place the next page must resume from. Truncating
        // first and filtering second is what keeps that distinction - see this class's own javadoc and
        // JdbcNotificationCentreTest#aFullPageWithADroppedRowStillReportsMoreRemain.
        var hasMore = raw.size() > size;
        var window = hasMore ? raw.subList(0, size) : raw;
        var entries = window.stream().map(Row::entry).filter(Objects::nonNull).toList();
        if (!hasMore) {
            return new NotificationPage(entries, false, null, null);
        }
        // The last row SERVED, never the extra one read to answer hasMore: resuming from that one would skip
        // whichever row sat between them, silently, on every full page. A hasMore window is never empty - it holds
        // exactly size rows, and size is positive by the guard above - so there is always a last row to name.
        var resumeFrom = window.getLast();
        return new NotificationPage(entries, true, resumeFrom.sentAt(), resumeFrom.id());
    }

    @Override
    public int unreadCount(String userId) {

        var count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM communication_delivery
                 WHERE organization_id = current_setting('app.current_org', true)
                   AND user_id = :user
                   AND channel = 'IN_APP'
                   AND status = 'SENT'
                   AND read_at IS NULL
                """, new MapSqlParameterSource().addValue("user", userId), Integer.class);

        // A count(*) can never actually come back null, but queryForObject's return type says it can - the same
        // null-safe unwrap this codebase's other Integer.class count reads use (see PreferenceRepositoryImpl and
        // ArtifactTagWriter) rather than let the auto-unboxing to int stand as an unguarded dereference.
        return Objects.requireNonNullElse(count, 0);
    }

    @Override
    public int markRead(String userId, long id, Instant now) {

        return jdbc.update("""
                UPDATE communication_delivery
                   SET read_at = :now, updated_at = :now
                 WHERE id = :id
                   AND organization_id = current_setting('app.current_org', true)
                   AND user_id = :user
                   AND channel = 'IN_APP'
                   AND status = 'SENT'
                   AND read_at IS NULL
                """, new MapSqlParameterSource().addValue("id", id).addValue("user", userId).addValue("now",
                Timestamp.from(now)));
    }

    @Override
    public int markAllRead(String userId, Instant now) {

        return jdbc.update("""
                UPDATE communication_delivery
                   SET read_at = :now, updated_at = :now
                 WHERE organization_id = current_setting('app.current_org', true)
                   AND user_id = :user
                   AND channel = 'IN_APP'
                   AND status = 'SENT'
                   AND read_at IS NULL
                """, new MapSqlParameterSource().addValue("user", userId).addValue("now", Timestamp.from(now)));
    }

    /**
     * A row whose message type this deploy cannot build an entry for is dropped rather than failing the whole page: an
     * older deploy's row, or one whose type this edition does not send, must not be able to empty a member's panel. It
     * is the same tolerance {@code DeliveryGate} shows a row it cannot resolve a type for.
     *
     * <p>
     * Dropped means its {@code entry} is null, not that the row vanishes: its {@code (sentAt, id)} is still the place
     * in the keyset the next page resumes from, and a mapper that answered null outright would throw that away along
     * with the entry. That is what left a page whose every row was dropped with no cursor to hand back.
     */
    private Row toRow(ResultSet rs, int rowNum) throws SQLException {

        return new Row(rs.getLong("id"), rs.getTimestamp("sent_at").toInstant(), toEntry(rs));
    }

    private NotificationEntry toEntry(ResultSet rs) throws SQLException {

        var id = rs.getLong("id");
        var sentAt = rs.getTimestamp("sent_at").toInstant();
        var read = rs.getTimestamp("read_at") != null;
        var facts = rs.getString("facts");
        return MessageType.parse(rs.getString("message_type"))
                .flatMap(type -> builders.build(type, FactRehydration.parse(jsonMapper, facts)))
                .map(partial -> entry(id, sentAt, read, partial)).orElse(null);
    }

    private static NotificationEntry entry(long id, Instant sentAt, boolean read,
            NotificationEntryBuilders.PartialEntry partial) {

        return NotificationEntry.builder().id(id).sender(partial.sender()).headline(partial.headline())
                .line(partial.line()).actionUrl(partial.actionUrl()).sentAt(sentAt).read(read).build();
    }

    /** One row of the raw window: its place in the keyset, and the entry it renders to - null when it was dropped. */
    private record Row(long id, Instant sentAt, NotificationEntry entry) {
    }
}
