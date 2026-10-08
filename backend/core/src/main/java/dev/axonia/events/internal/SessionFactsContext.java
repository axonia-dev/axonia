package dev.axonia.events.internal;

import java.util.List;
import java.util.Map;

import dev.axonia.community.CommunityIdentity;
import dev.axonia.events.ResourceLink;
import lombok.Builder;

/**
 * Everything {@link SessionFacts#of} needs but a bare {@link dev.axonia.events.Session} cannot supply on its own: a
 * resolved presenter display name, a working share URL, the series' own title, what comes next in the series, the
 * resources attached to each slot and to the session itself, the session's action points and what it checked. Every one
 * of these sits behind either a repository ({@code SeriesRepository}, {@code SessionRepository},
 * {@code ActionItemRepository}, {@code ActionItemCheckpointRepository} - same module, same package as the producer that
 * will build this) or a cross-module port ({@code UserProfileAccess} for names, {@code CommunityAccess} for the slug a
 * URL needs, {@code SessionResourceAccess} for resources, which {@code knowledge} owns). The caller resolves each of
 * these once per session and hands the result in here, so {@link SessionFacts#of} stays a pure function of its two
 * arguments - no repository access, no port, no clock of its own.
 *
 * <p>
 * {@code sessionUrl} alone is required, not optional: unlike {@code seriesTitle}, {@code nextSession} and the two
 * resource carriers - each of which has a genuine absent state (a one-off session, a series' final occurrence, a
 * session or slot with nothing attached) - there is no message this context feeds that does not want its call to
 * action. A silently blank link is not a lesser version of the mail, it is three messages - including the
 * awaiting-close nudge's "Close out this session", the entire point of that nudge - shipped with their button gone and
 * nothing to say so. Rejecting it here, at construction, turns a caller's forgotten builder call into an exception the
 * first time it happens rather than a silent gap nobody notices.
 *
 * <p>
 * {@code resourcesBySlotId} and {@code sessionResources} both carry {@link ResourceLink}, never a bare URL: a resource
 * is a title and a URL together, and narrowing to the URL alone before {@link SessionFacts} ever sees it would throw
 * the title away for every consumer, not just the ones that do not want it.
 *
 * <p>
 * The maps and lists here are held by reference, not deep-copied: this is a short-lived value carrier, built once by
 * the producer that resolves everything above and consumed once by {@link SessionFacts#of}, the same shape as
 * {@code OutboundMessage} elsewhere in this codebase. {@link SessionFacts#of} still hands its own caller an
 * unmodifiable view of every list it builds - that guards the template from mutating what {@code SessionFacts}
 * constructs, not from a caller who kept a mutable reference to a collection handed in here and mutates it after
 * construction.
 *
 * <p>
 * {@code community} and {@code communityId} travel together and are read together: the identity supplies the name and
 * the accent, and the id is what {@link AccentTint} hashes when a community has chosen no accent. The id is not read
 * off the identity because the identity deliberately does not carry it - it is the key the caller already had in hand
 * to look the identity up with.
 *
 * <p>
 * {@code nudgeArm} is set only by the awaiting-close nudge, and names which of that producer's two firings enqueued
 * this row. It is a stored token rather than an elapsed time on purpose: see {@link SessionFacts}.
 *
 * <p>
 * {@code backlogUrl} is the community's backlog page - the quiet second act the upcoming notice offers once its agenda
 * already has something on it. Built by the caller from the exact same community slug that {@code sessionUrl} is built
 * from, never resolved a second time.
 */
@Builder
public record SessionFactsContext(String sessionUrl, Map<String, String> displayNamesByUserId, String seriesTitle,
        String nextSession, Map<Long, List<ResourceLink>> resourcesBySlotId, List<ResourceLink> sessionResources,
        List<ActionPointSource> actionPoints, List<CheckedItemSource> checkedItems, CommunityIdentity community,
        Long communityId, String nudgeArm, String backlogUrl) {

    public SessionFactsContext {
        if (sessionUrl == null || sessionUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "sessionUrl must not be blank: every message this context feeds wants a working link");
        }
        displayNamesByUserId = displayNamesByUserId == null ? Map.of() : displayNamesByUserId;
        resourcesBySlotId = resourcesBySlotId == null ? Map.of() : resourcesBySlotId;
        sessionResources = sessionResources == null ? List.of() : sessionResources;
        actionPoints = actionPoints == null ? List.of() : actionPoints;
        checkedItems = checkedItems == null ? List.of() : checkedItems;
    }
}
