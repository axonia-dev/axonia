package dev.axonia.learning.application.port.out;

import java.util.List;
import java.util.Set;

import dev.axonia.learning.domain.LearningIntent;

/**
 * Read/write port for the areas members want to learn. Every method passes {@code org} explicitly as defence in depth,
 * even though RLS already scopes the table on {@code app.current_org}.
 */
public interface LearningIntentRepository {

    /**
     * Taken by one member's add, removal and departure in one community, until the transaction ends. Without it,
     * parallel adds of distinct tags all pass the cap check before any of them inserts, and an add racing a leave
     * strands its row.
     */
    void lockLearner(String org, long communityId, String userId);

    /** Every intent one member stated in one community, earliest first. */
    List<LearningIntent> inCommunity(String org, long communityId, String userId);

    /** Inserts the intent; a second insert of the same (community, user, tag) is a no-op. */
    void insert(String org, LearningIntent intent);

    /** Deletes the member's intents of any of {@code tags} in the community. */
    void delete(String org, long communityId, String userId, Set<String> tags);

    /** Every intent one member stated in one community, for when they leave it. */
    void deleteAllIn(String org, long communityId, String userId);

    /**
     * Every intent the community's current members hold, under every spelling they typed: the learner active in the
     * directory and still a member. Someone who left or was deactivated drops out at once.
     */
    List<LearningIntent> currentIn(String org, long communityId);

    /**
     * Every tag an intent in this community carries, whoever holds it: a name the curation page must be able to merge,
     * rename or delete is one the community produced.
     */
    List<String> tagsIn(String org, long communityId);

    /** How many members hold an intent of exactly this tag in the community, whoever they are. */
    int countIn(String org, long communityId, String tag);

    /**
     * A curator's rename in one community: rewrites the tag, folding into an existing intent of the target and keeping
     * the earlier date.
     */
    void renameTag(String org, long communityId, String from, String to);

    /** A curator's delete in one community. */
    void removeTag(String org, long communityId, String tag);
}
