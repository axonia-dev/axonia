package dev.axonia.roundup;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/**
 * Read-only session activity for one whole organization over a window [from, to). Implemented in
 * dev.axonia.events.internal, so events depends on the roundup and the roundup depends on nothing - the same direction
 * {@code health.EventsSignals} already runs in.
 *
 * <p>
 * Every method names its organization explicitly rather than reading one from a request: a roundup is assembled by a
 * scheduler and drained by a cross-tenant sweep, neither of which carries a JWT. The parameter is not the whole
 * contract. Callers MUST run inside {@code OrganizationScope.runAs(organizationId, ...)}, and that {@code runAs} MUST
 * wrap the {@code @Transactional} entry point rather than sit inside it: {@code community_session} is row-level-secured
 * on {@code app.current_org}, which Postgres chooses when the transaction acquires its connection. A scheduler thread
 * with no scope leaves that setting unset, and under the {@code axonia_app} role every method here then answers an
 * empty list or zero instead of failing - a roundup that reports a quarter in which nothing happened. No adapter test
 * can catch it either: Testcontainers connects as the table owner, and ownership bypasses the policy.
 *
 * <p>
 * It deliberately answers nothing per member and nothing per community: no attendance, no engagement, no per-person
 * counts. A roundup is one organization-wide letter, so a signal it cannot print has no business crossing this
 * boundary, and presence in an inbox is not a reason to widen what personal data leaves events.
 */
public interface SessionActivity {

    /**
     * The sessions that took place in the window, oldest first. Cancelled and skipped occurrences are excluded: both
     * deliberately did not happen, and a roundup that lists them is reporting meetings nobody attended.
     *
     * <p>
     * A past occurrence nobody ever closed IS included, even though nobody asserted it happened. Organizers routinely
     * forget to write a session up - the awaiting-close nudge exists for exactly that - so requiring the assertion
     * would drop real meetings from the quarter they belong to, and a roundup that under-reports the organization's own
     * work is worse than one that credits a meeting somebody skipped without saying so. The same rule reaches
     * {@link #firstTimePresenterCount}, so the two cannot disagree about which sessions counted.
     */
    List<HeldSession> heldInPeriod(String organizationId, Instant from, Instant to);

    /**
     * What is still on the calendar in the window, soonest first: occurrences nobody has closed, skipped or cancelled.
     * The roundup looks forward as well as back, and an occurrence that has already been dealt with is not an
     * invitation.
     */
    List<UpcomingSession> scheduledAfter(String organizationId, Instant from, Instant to);

    /**
     * How many talks in the window have nobody presenting them. The one figure in the roundup that ASKS the reader for
     * something rather than reporting, so it counts SLOTS rather than sessions: "four talks still need a presenter" is
     * an invitation, and "three sessions have a gap" is a status report.
     *
     * <p>
     * Scoped to {@code SCHEDULED} occurrences alone, the same set {@link #scheduledAfter} answers with: an empty slot
     * on a cancelled session is not an invitation, and counting it would ask people to fill a gap in a meeting that is
     * not happening.
     *
     * <p>
     * <b>It is organization-wide and deliberately unfiltered by community, which the rest of the letter is not.</b>
     * Every other figure the roundup prints is narrowed to the communities {@code CommunityActivity#activeCommunities}
     * still names, because naming a closed community invites people into a room that no longer exists. This count is
     * not: a scheduled session in a community archived since the quarter closed is included here. That is accepted
     * rather than overlooked. The figure names nobody and links nowhere - it is a number above one call to action
     * pointing at the community index - so the worst case is an ask that is a little larger than the index can account
     * for, which is a far smaller harm than the alternative of a second, subtly different community filter growing
     * beside the one {@code RoundupFacts} already applies.
     */
    int openTalkCount(String organizationId, Instant from, Instant to);

    /**
     * How many people presented in the window and had never presented before.
     *
     * <p>
     * "Never before" is organization-wide, not per community: somebody who has presented in another guild is not a
     * first-time contributor to the organization, and this number is printed at organization scale. Counting it per
     * community would inflate the same person once per guild they are new to, which reads as growth that never
     * happened.
     */
    int firstTimePresenterCount(String organizationId, Instant from, Instant to);

    /**
     * One session that took place. {@code presenterNames} are resolved names, never user ids: the roundup renders this
     * list into a mail, and an id that reached an inbox would render literally. A presenter nobody can resolve is
     * absent from the list rather than present as an identifier, and {@code title} may be null for a session that
     * carries no title anywhere in it - both are states the renderer handles, an id is not.
     *
     * <p>
     * {@code talkCount} is how many talks the session's agenda carried, across all of its sections, presented or not.
     * It is a separate number from {@code presenterNames.size()} and must stay one: a talk with two presenters is one
     * talk, a talk nobody presented is still a talk, and one person presenting twice is one person. The roundup's
     * headline states both, so deriving either from the other would print the same figure twice under two labels.
     *
     * <p>
     * {@code talkTitles} names the titled slots in agenda order and is deliberately NOT the same length as
     * {@code talkCount}: a slot with no title of its own is counted and not named, because a row reading "Untitled
     * talk" tells a reader less than the session's own heading already did. Neither may be derived from the other.
     *
     * <p>
     * {@code sessionId} is here so activity read in other modules - the artifacts attached to this session, the tags
     * its talks carry - can be joined back to it. It never reaches a template.
     */
    @Builder
    record HeldSession(long sessionId, long communityId, String title, Instant heldAt, int talkCount,
            List<String> talkTitles, List<String> presenterNames) {
    }

    /**
     * One session still to come: enough to name it on the community's card and say when it is.
     *
     * <p>
     * It used to carry a {@code signUpOpen} flag as well, meaning at least one of its talks had nobody presenting it.
     * That was the right fact while the letter rendered a flat "What is coming" list, where a per-row invitation had
     * somewhere to appear. Folding that list into the community cards left the flag with no reader:
     * {@code RoundupFacts#communityCard} names the next session and its date and nothing else, and the ask is carried
     * organization-wide by {@link #openTalkCount}. A fact nobody reads is a fact nobody maintains, so it is gone rather
     * than kept against a rendering that might come back.
     */
    record UpcomingSession(long communityId, String title, Instant scheduledAt) {
    }
}
