package dev.axonia.expertise.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The writes every table of {@link MemberTag}s shares. Each is keyed by (organization, community, member, tag), holds
 * the tag as typed and is read through the community's merges, so adding, removing, leaving and a curator's rename or
 * delete work the same way on every one of them. Every method passes {@code org} explicitly as defence in depth, even
 * though RLS already scopes each table.
 */
public interface MemberTagStore<T extends MemberTag> {

    /** Held until the transaction ends: one member's writes in one community run one at a time. */
    void lockMember(String org, long communityId, String userId);

    /** Every row one member holds in one community, earliest first. */
    List<T> inCommunity(String org, long communityId, String userId);

    /**
     * Every row in one community whose member is active and still belongs to it, earliest first, then by member and
     * tag.
     */
    List<T> activeIn(String org, long communityId);

    /** A second insert of the same (community, member, tag) is a no-op, so the first date stands. */
    void insert(String org, T row);

    void delete(String org, long communityId, String userId, Set<String> tags);

    void deleteAllIn(String org, long communityId, String userId);

    /** Every tag the table carries in the community, each once, whoever holds it. */
    List<String> tagsIn(String org, long communityId);

    /**
     * Rewrites the tag in one community, folding into a row the member already holds under the target and keeping the
     * earlier date. Several statements: the caller's transaction must hold them together.
     */
    void renameTag(String org, long communityId, String from, String to);

    void removeTag(String org, long communityId, String tag);

    /** Builds a row from the four columns every such table carries; a record's canonical constructor fits. */
    @FunctionalInterface
    interface Factory<T> {

        T of(long communityId, String userId, String tag, Instant declaredAt);
    }
}
