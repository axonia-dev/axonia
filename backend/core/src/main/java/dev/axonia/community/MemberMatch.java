package dev.axonia.community;

/**
 * The outcome of resolving a free-text reference (a display name, an email, or a user id) to a member of a community.
 *
 * <p>
 * An address is never synthesized from a name: a wrong guess silently credits one person's work to another, and nobody
 * would ever notice. A reference that does not resolve to exactly one member of the community resolves to nobody.
 */
public sealed interface MemberMatch {

    /** Exactly one member of the community matched. */
    record Matched(String userId) implements MemberMatch {
    }

    /** No member of the community matched. */
    record NotFound() implements MemberMatch {
    }

    /**
     * More than one member matched. The count is reported; the candidates deliberately are not, so this cannot become a
     * member-directory read in disguise. The roster, behind its own scope, is the sanctioned way to see people.
     */
    record Ambiguous(int candidates) implements MemberMatch {
    }
}
