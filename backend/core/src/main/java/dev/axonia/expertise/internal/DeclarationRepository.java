package dev.axonia.expertise.internal;

import java.util.List;
import java.util.Set;

import dev.axonia.expertise.Declaration;

/**
 * Read/write port for declared areas. Every method passes {@code org} explicitly as defence in depth, even though RLS
 * already scopes the table on {@code app.current_org}.
 */
public interface DeclarationRepository {

    /**
     * Taken by one member's declare, removal and departure listener in one community, until the transaction ends.
     * Without it, parallel declares of distinct tags all pass the cap check before any of them inserts, and a declare
     * racing a leave strands its row.
     */
    void lockDeclarer(String org, long communityId, String userId);

    /** Every declaration one member made in one community, earliest first. */
    List<Declaration> inCommunity(String org, long communityId, String userId);

    /** Inserts the declaration; a second insert of the same (community, user, tag) is a no-op. */
    void insert(String org, Declaration declaration);

    /** Deletes the member's declarations of any of {@code tags} in the community. */
    void delete(String org, long communityId, String userId, Set<String> tags);

    /** Every declaration one member made in one community, for when they leave it. */
    void deleteAllIn(String org, long communityId, String userId);

    /** Every declaration one member made, across communities, regardless of opt-out or hidden areas. */
    List<Declaration> forUser(String org, String userId);

    /**
     * The declarations "who knows X?" may show: raw tag in {@code tags}, the declarer active in the directory and a
     * current member of the community, not opted out, and not hiding the area the community's aliases read the tag as.
     * Tags are expanded by the caller, exactly as for {@link ExpertiseQueryRepository#findPractitioners}.
     */
    List<Declaration> visibleIn(String org, long communityId, Set<String> tags);

    /**
     * Every declaration the community's areas list may count, under the visibility rule of
     * {@link #visibleIn(String, long, Set)} and over every tag at once: one read for the whole community rather than
     * one per area.
     */
    List<Declaration> allVisibleIn(String org, long communityId);

    /**
     * Every tag this community's members declared, whoever they are: a name the curation page must be able to merge,
     * rename or delete is one the community produced, not one anybody is currently findable under.
     */
    List<String> tagsIn(String org, long communityId);

    /**
     * How many members declared this community's area, counted over the spellings {@link #removeTag} would remove, so a
     * delete can name them before it runs. One member counts once however many spellings of the area they typed.
     */
    int countIn(String org, long communityId, Set<String> tags);

    /**
     * A curator's rename in one community: rewrites the tag, folding into an existing declaration of the target and
     * keeping the earlier date. Call after {@link SignalWriter#carryHiddenArea}, which reads the declarations under
     * their old names.
     */
    void renameTag(String org, long communityId, String from, String to);

    /** A curator's delete in one community. */
    void removeTag(String org, long communityId, String tag);
}
