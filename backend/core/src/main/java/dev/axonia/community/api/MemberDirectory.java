package dev.axonia.community.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Who the members of a community are called, and which person in the organization a free-text reference means. */
public interface MemberDirectory {

    /**
     * Enough candidates to detect ambiguity without paging a large roster into memory.
     *
     * <p>
     * Public because an ambiguity count is a lower bound capped by exactly this number, and any surface that reports
     * one must say so with the real figure. A surface that hard-codes it silently starts lying the day it moves.
     */
    int CANDIDATE_LIMIT = 25;

    /**
     * Which person in the organization a free-text reference credits: a user id, a display name or an email. Anybody
     * present who holds a subject can be credited, members of the community first; crediting makes nobody a member.
     */
    MemberMatch resolvePerson(long communityId, String query);

    /** Whether a reference names this user exactly: their display name or email, normalized. */
    boolean identifiesExactly(String userId, String reference);

    /** The community's roster, one page at a time, each member carrying a display name. */
    Page<CommunityMember> list(long communityId, Pageable pageable);
}
