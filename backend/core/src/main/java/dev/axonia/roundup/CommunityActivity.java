package dev.axonia.roundup;

import java.util.List;

/**
 * Read-only community facts for one whole organization. Implemented in dev.axonia.community.internal, so community
 * depends on the roundup and never the other way round.
 *
 * <p>
 * The organization arrives as a parameter because a roundup is assembled with no JWT in scope, and that is not the
 * whole contract. Callers MUST run inside {@code OrganizationScope.runAs(organizationId, ...)}, and that {@code runAs}
 * MUST wrap the {@code @Transactional} entry point rather than sit inside it: {@code communities} is row-level-secured
 * on {@code app.current_org}, which Postgres chooses when the transaction acquires its connection. A scheduler thread
 * with no scope leaves that setting unset, and under the {@code axonia_app} role this method then answers an empty list
 * instead of failing - an organization that appears to have no communities at all. No adapter test can catch it either:
 * Testcontainers connects as the table owner, and ownership bypasses the policy.
 *
 * <p>
 * It deliberately answers nothing about membership: not who belongs to what, not how many. A roundup goes to everybody
 * in the organization rather than to a roster, so a community is named here only so activity read elsewhere can be
 * attributed to something a reader recognises.
 */
public interface CommunityActivity {

    /**
     * The organization's live communities, by name. Archived ones are excluded: a roundup naming a community that was
     * closed months ago is inviting people into a room that no longer exists.
     */
    List<CommunitySummary> activeCommunities(String organizationId);

    /**
     * Enough of a community to name it and to render its mark on the card. {@code badgeKey} is the object key of an
     * uploaded badge and is null for a community that never set one; {@code accentColor} is its chosen hex and is null
     * for the same reason. The roundup falls back to an initials monogram tinted by the shell's neutral pair when
     * either is absent, so neither is a value a renderer may assume is present.
     *
     * <p>
     * There is deliberately no {@code slug}, and there was one. The letter does not link a community - a roundup
     * carries ONE call to action, and eight community links racing it is how that rule dies quietly - so a slug here
     * was a value nothing could read, and the next person to see it would have wired it into an href without noticing
     * why it had never been used. {@code QuarterActivity.NamedCommunity} records the same reasoning one layer in.
     */
    record CommunitySummary(long id, String name, String badgeKey, String accentColor) {
    }
}
