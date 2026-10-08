package dev.axonia;

import java.util.List;

/**
 * Rewrites one tag across the content a bounded context owns, for a single community. Each module holding tagged
 * content implements this inside its {@code internal} package; the expertise context injects the full list and invokes
 * it inside the single curation transaction.
 *
 * <p>
 * Implementations touch only their own module's tables and rely on the caller's RLS scope (the connection's
 * {@code app.current_org}) as the outer isolation guarantee, exactly as {@link OrganizationDataPurger} does. The
 * community bound is not advisory: a curator's authority comes from a per-community grant, so an implementation that
 * rewrote another community's rows would hand them power no policy gave them.
 */
public interface CommunityTagWriter {

    /** Every tag this module's records carry in the community, each once, whoever carries it. */
    List<String> tagsIn(long communityId);

    /** How much of this module's content in the community carries the tag. Empty when it carries none. */
    List<TagUsage> usage(long communityId, String tag);

    /**
     * Retags content carrying {@code from} with {@code to}. When a record already carries {@code to}, the two fold into
     * one - the record is left carrying {@code to} exactly once.
     */
    void rename(long communityId, String from, String to);

    /** Strips the tag from every record in the community that carries it. */
    void remove(long communityId, String tag);
}
