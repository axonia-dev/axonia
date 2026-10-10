package dev.axonia.expertise.application.port.out;

import java.util.List;
import java.util.Set;

import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.ExpertiseSignal;
import dev.axonia.expertise.domain.UserSignals;
import dev.axonia.expertise.domain.UserTagAggregate;

/**
 * Read-side query port for the expertise projection.
 *
 * <p>
 * All methods operate within the RLS scope established by the caller (app.current_org). Callers are responsible for
 * expanding canonical tags to their variants via {@link AliasResolver} before invoking {@link #findPractitioners}: this
 * repository performs a plain {@code tag IN (:tags)} match.
 */
public interface ExpertiseQueryRepository {

    /**
     * Returns signals for all practitioners in the given community whose tag is in {@code tags}, grouped per user.
     * Excludes:
     * <ul>
     * <li>any user present in {@code expertise_opt_out},</li>
     * <li>any signal whose area, the tag its community's aliases read it as, the owning user hid in
     * {@code expertise_hidden_tag},</li>
     * <li>any signal dismissed in {@code expertise_dismissed_signal} under its area or its stored tag.</li>
     * </ul>
     *
     * @param org
     *            organization ID (must match the active RLS session variable)
     * @param community
     *            community ID to scope the search
     * @param tags
     *            canonical tag plus any known variants (expansion done by caller)
     */
    List<UserSignals> findPractitioners(String org, Long community, Set<String> tags);

    /**
     * Returns ALL signals for a single user in the given organization, regardless of opt-out or hidden-tag preferences.
     * Intended for the self-view so users can see what would appear to others if they were not opted out or had not
     * hidden a tag.
     *
     * <p>
     * Returns a single-element list (or empty if no signals exist) using the same {@link UserSignals} wrapper as
     * {@link #findPractitioners} for consistency with callers that process both results uniformly.
     *
     * @param org
     *            organization ID
     * @param userId
     *            the user whose full signal set to retrieve
     */
    List<UserSignals> findForUser(String org, String userId);

    /**
     * The given user's signals across the organization that survive the visibility controls, through the predicate
     * {@link #findPractitioners} applies: an inactive or absent {@code identity_user}, an opt-out, a hidden area, and a
     * dismissed signal. Tags are raw, not canonical. Where {@link #findForUser} shows what was recorded, this reads
     * what the community sees.
     *
     * @param org
     *            organization ID
     * @param userId
     *            the user whose visible signal set to retrieve
     */
    List<ExpertiseSignal> findVisibleForUser(String org, String userId);

    /**
     * {@link #findForUser} inside one community: every signal the user has in it, regardless of opt-out, hidden areas
     * or dismissals, so the member's own view of that community shows what was recorded there.
     *
     * @param org
     *            organization ID
     * @param community
     *            the community the view is narrowed to
     * @param userId
     *            the user whose signal set to retrieve
     */
    List<ExpertiseSignal> findForUserIn(String org, long community, String userId);

    /**
     * {@link #findVisibleForUser} inside one community: the user's signals there that survive the visibility controls.
     *
     * @param org
     *            organization ID
     * @param community
     *            the community the view is narrowed to
     * @param userId
     *            the user whose visible signal set to retrieve
     */
    List<ExpertiseSignal> findVisibleForUserIn(String org, long community, String userId);

    /**
     * Returns all distinct <em>raw</em> tags recorded in {@code expertise_signal} for the given org + community. These
     * are not canonicalized: merged variants are still present, so callers that need canonical tags must collapse them
     * via {@link AliasResolver#canonicalOf} using {@link TagAliasRepository#aliasMap}. Used by area listing and
     * curation.
     *
     * @param org
     *            organization ID
     * @param community
     *            community ID
     */
    List<String> allTags(String org, Long community);

    /**
     * Returns one row per surviving {@code (user, raw tag)} in the community, with signal counts per kind and the most
     * recent occurrence. Applies exactly the exclusions {@link #findPractitioners} applies: an inactive or absent
     * {@code identity_user}, an opt-out, a hidden tag, and a dismissed signal.
     *
     * <p>
     * Tags are raw, not canonical: callers fold them via {@link TagAliasRepository#aliasMap}, as the area listing does.
     * One query rather than one per tag, because the topic map needs counts for every pair of tags and a per-tag query
     * would make that quadratic.
     */
    List<UserTagAggregate> userTagAggregates(String org, Long community);

    /**
     * Every signal in the community that survives the visibility controls, newest first, capped at {@code limit}.
     * Applies exactly the exclusions {@link #findPractitioners} applies, through the same shared predicate: an inactive
     * or absent {@code identity_user}, an opt-out, a hidden area, and a dismissed signal. Tags are raw, not canonical,
     * so callers fold them via {@link TagAliasRepository#aliasMap}.
     *
     * <p>
     * A member who opted out contributes nothing here, and nothing records that they were removed: their absence is
     * indistinguishable from never having contributed, which is the property the opt-out has to have.
     */
    List<ExpertiseSignal> visibleSignals(String org, Long community, int limit);

}
