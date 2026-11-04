package dev.axonia.community.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.axonia.community.api.MemberDirectory;
import dev.axonia.community.api.MemberMatch;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;

/**
 * The rule that picks the one person a reference credits, or refuses to guess.
 *
 * <p>
 * An ambiguity's count is capped at {@link MemberDirectory#CANDIDATE_LIMIT}, because it is reported as a lower bound
 * that stops counting there.
 */
final class CreditCandidates {

    private CreditCandidates() {
    }

    /** Everybody whose whole name or email is the reference: one is them, and of several a single member is. */
    static MemberMatch exactly(List<DirectoryUser> exact, Set<String> memberIds) {

        if (exact.size() == 1) {
            return new MemberMatch.Matched(exact.getFirst().userId());
        }
        var members = exact.stream().filter(user -> memberIds.contains(user.userId())).toList();
        return members.size() == 1 ? new MemberMatch.Matched(members.getFirst().userId()) : ambiguous(exact.size());
    }

    /**
     * Nobody is named exactly: a member who alone matches the name keeps it over namesakes elsewhere, then somebody who
     * alone matches across the organization.
     */
    static MemberMatch byName(List<DirectoryUser> roster, List<DirectoryUser> everyone) {

        if (roster.size() == 1) {
            return new MemberMatch.Matched(roster.getFirst().userId());
        }
        var candidates = new LinkedHashSet<String>();
        roster.forEach(user -> candidates.add(user.userId()));
        everyone.forEach(user -> candidates.add(user.userId()));
        if (candidates.isEmpty()) {
            return new MemberMatch.NotFound();
        }
        return candidates.size() == 1 ? new MemberMatch.Matched(candidates.getFirst()) : ambiguous(candidates.size());
    }

    private static MemberMatch ambiguous(int candidates) {

        return new MemberMatch.Ambiguous(Math.min(candidates, MemberDirectory.CANDIDATE_LIMIT));
    }
}
