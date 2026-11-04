package dev.axonia.community.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;

/**
 * The people a reference could credit, gathered from the community's roster and from the whole organization, and the
 * rule that picks one of them or refuses to guess.
 *
 * <p>
 * The roster is kept apart from the organization-wide candidates because each answers a question the other cannot: the
 * roster cannot be pushed out of a capped search by namesakes elsewhere, and only the organization knows the people who
 * never joined.
 */
record CreditCandidates(List<DirectoryUser> roster, List<DirectoryUser> everyone, Set<String> memberIds) {

    static CreditCandidates of(List<DirectoryUser> roster, List<DirectoryUser> organization, Set<String> memberIds) {

        var union = new LinkedHashMap<String, DirectoryUser>();
        roster.forEach(user -> union.putIfAbsent(user.userId(), user));
        organization.forEach(user -> union.putIfAbsent(user.userId(), user));
        return new CreditCandidates(roster, List.copyOf(union.values()), memberIds);
    }

    MemberMatch match(String normalizedReference) {

        var exact = everyone.stream().filter(user -> MemberDirectoryService.matchesExactly(user, normalizedReference))
                .toList();
        if (exact.size() == 1) {
            return matched(exact.getFirst());
        }
        if (exact.size() > 1) {
            var exactMembers = exact.stream().filter(user -> memberIds.contains(user.userId())).toList();
            return exactMembers.size() == 1
                    ? matched(exactMembers.getFirst())
                    : new MemberMatch.Ambiguous(exact.size());
        }
        return unique(roster).or(() -> unique(everyone)).map(Function.<MemberMatch>identity()).orElseGet(
                () -> everyone.isEmpty() ? new MemberMatch.NotFound() : new MemberMatch.Ambiguous(everyone.size()));
    }

    private static Optional<MemberMatch.Matched> unique(List<DirectoryUser> candidates) {

        return candidates.size() == 1 ? Optional.of(matched(candidates.getFirst())) : Optional.empty();
    }

    private static MemberMatch.Matched matched(DirectoryUser user) {

        return new MemberMatch.Matched(user.userId());
    }
}
