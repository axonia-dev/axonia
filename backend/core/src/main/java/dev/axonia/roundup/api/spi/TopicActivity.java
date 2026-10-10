package dev.axonia.roundup.api.spi;

import java.util.Collection;
import java.util.List;

/**
 * Read-only topic activity for the sessions one quarter held. Implemented by
 * dev.axonia.events.adapter.out.roundup.TopicActivityAdapter, so events depends on the roundup and never the other way
 * round.
 *
 * <p>
 * The organization arrives as a parameter because a roundup is assembled with no JWT in scope, and that is not the
 * whole contract. Callers MUST run inside {@code OrganizationScope.runAs(organizationId, ...)}, and that {@code runAs}
 * MUST wrap the {@code @Transactional} entry point rather than sit inside it: {@code community_session} is
 * row-level-secured on {@code app.current_org}, which Postgres chooses when the transaction acquires its connection. An
 * unscoped read answers an empty list rather than failing, and no owner-connected test can catch it.
 *
 * <p>
 * It answers a tag and a count and nothing else. The letter renders the tags as a weighted cloud, which is a picture of
 * what the quarter was about rather than a claim about anybody: a tag is not attributed to the person who used it, and
 * this port has nothing to attribute it with.
 */
public interface TopicActivity {

    /**
     * How often each tag was used across the given sessions' talks, busiest first and alphabetical within a tie. A slot
     * carrying the same tag twice is impossible - {@code event_slot_tag}'s primary key is {@code (slot_id, tag)} - so
     * this counts slots, not tag rows.
     *
     * <p>
     * An empty {@code sessionIds} answers an empty list without querying, because {@code IN ()} is not valid SQL.
     */
    List<TagCount> tagsForSessions(String organizationId, Collection<Long> sessionIds);

    /** One tag and how many of the quarter's talks carried it. */
    record TagCount(String tag, int count) {
    }
}
