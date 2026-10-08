package dev.axonia.communication;

import java.time.Instant;
import java.util.List;

/**
 * One screenful of a member's panel, whether anything remains behind it, and where to resume from. {@code hasMore} is
 * answered by reading one row more than was asked for rather than by counting the whole list, which a panel that only
 * ever pages forward never needs.
 *
 * <p>
 * {@code nextBefore} and {@code nextBeforeId} are the cursor of the last row of the window this page was cut from, and
 * are non-null exactly when {@code hasMore} is true. They exist because a caller cannot derive that cursor from
 * {@code entries}: a row whose {@code MessageType} this deploy no longer declares is dropped from {@code entries} but
 * still occupies its place in the window, so a page whose every row was dropped comes back empty with more genuinely
 * behind it. A client deriving the cursor from its last visible entry has none to derive, stops paging, and strands
 * every older readable entry behind that one unreadable window, permanently. The cursor is the server's to state
 * because only the server saw the rows it is cut from.
 */
public record NotificationPage(List<NotificationEntry> entries, boolean hasMore, Instant nextBefore,
        Long nextBeforeId) {
}
