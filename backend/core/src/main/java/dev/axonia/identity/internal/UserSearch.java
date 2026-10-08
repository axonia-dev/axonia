package dev.axonia.identity.internal;

import java.util.Collection;

import lombok.Builder;

/**
 * One directory search: a free-text query matched against the display names and emails of an organization's users,
 * optionally narrowed to a known set of them.
 *
 * <p>
 * {@code restrictToUserIds} is three-valued on purpose. {@code null} means "the whole organization" (the admin roster
 * and the paged people list). A populated collection means "only these people" (resolving a name against a community's
 * members, where the narrowing must happen before the limit bites, or a genuine member can be pushed out of the results
 * by non-member namesakes). An empty collection means "nobody", which is what a community with no members matches.
 *
 * <p>
 * {@code includeDeparted} defaults to false, so a search is a picker search unless it says otherwise. Pickers and
 * suggestions must not offer somebody who has left; administrative rosters whose job is to show who exists set it.
 *
 * <p>
 * {@code nameOnly} matches the display name alone, never the email, so a member cannot probe another member's address
 * by trying fragments of it. Only a community's member picker sets it; every other search matches display name or
 * email.
 */
@Builder
record UserSearch(String organizationId, String query, Collection<String> restrictToUserIds, int limit, long offset,
        boolean includeDeparted, boolean nameOnly) {
}
