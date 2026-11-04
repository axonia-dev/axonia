package dev.axonia.community.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.CommunityMember;
import dev.axonia.community.api.CreditablePerson;
import dev.axonia.community.api.MemberMatch;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.MembershipRecord;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import lombok.RequiredArgsConstructor;

/**
 * Who the members of a community are called, and which person in the organization a free-text reference credits.
 *
 * <p>
 * Crediting reaches the whole organization, because the people a community credits - a presenter, a contributor, an
 * assignee - need not have joined it, and many have never signed in. It is still answered here rather than in
 * {@code identity} because it prefers members, which needs the roster this module owns: a member who alone matches a
 * reference keeps it over namesakes elsewhere, and the roster is searched on its own so its members cannot be pushed
 * out of an organization-wide search's limit.
 *
 * <p>
 * Exact-then-unique: an exact match on normalized display name or email wins outright, and of several exact matches a
 * single member wins; otherwise a token match is accepted only when it is unique, among members first and then across
 * the organization. Anything else resolves to nobody.
 */
@Service
@RequiredArgsConstructor
public class MemberDirectoryService implements dev.axonia.community.api.MemberDirectory {

    private final MembershipLedger membershipLedger;
    private final UserDirectory userDirectory;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public MemberMatch resolvePerson(long communityId, String query) {

        if (query == null || query.isBlank()) {
            return new MemberMatch.NotFound();
        }
        var organizationId = organizationContext.organizationId();
        var reference = query.trim();

        // A caller that knows the id exactly - the UI, or an agent that has just read the roster - is honoured
        // directly, rather than made to round-trip a known id through a fuzzy search that could come back ambiguous.
        if (membershipLedger.find(organizationId, communityId, reference).isPresent()
                || userDirectory.presentOf(organizationId, List.of(reference)).contains(reference)) {
            return new MemberMatch.Matched(reference);
        }

        var memberIds = membershipLedger.memberUserIds(organizationId, communityId);
        var roster = userDirectory.searchMembers(organizationId, memberIds, reference, CANDIDATE_LIMIT);
        var everyone = userDirectory.searchPeople(organizationId, reference, CANDIDATE_LIMIT);
        return CreditCandidates.of(roster, everyone, Set.copyOf(memberIds)).match(normalize(reference));
    }

    /**
     * Whether a reference names this user exactly: their display name or email, normalized. Only the user's own
     * directory row is read, so the answer reveals nothing about anybody else.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean identifiesExactly(String userId, String reference) {

        var normalized = normalize(reference);
        if (normalized.isEmpty()) {
            return false;
        }
        return userDirectory.byIds(organizationContext.organizationId(), List.of(userId)).stream()
                .anyMatch(user -> matchesExactly(user, normalized));
    }

    /**
     * The people whose name matches a typed query, for a picker: members of the community first, then anybody else in
     * the organization who can be credited, at most {@link #CANDIDATE_LIMIT} in all. A blank query names nobody, so it
     * returns nothing rather than an arbitrary first page of people.
     *
     * <p>
     * Matched by display name only, never by email: a member could otherwise probe anybody's email address by trying
     * fragments of it through the picker. {@link #resolvePerson} keeps matching email too, because the agent that calls
     * it names people by email.
     */
    @Transactional(readOnly = true)
    public List<CreditablePerson> searchPeople(long communityId, String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }
        var organizationId = organizationContext.organizationId();
        var text = query.trim();
        var memberIds = membershipLedger.memberUserIds(organizationId, communityId);
        var members = userDirectory.searchMembersByName(organizationId, memberIds, text, CANDIDATE_LIMIT);
        var everyone = userDirectory.searchPeopleByName(organizationId, text, CANDIDATE_LIMIT);
        var memberSet = Set.copyOf(memberIds);
        return Stream.concat(members.stream(), everyone.stream().filter(user -> !memberSet.contains(user.userId())))
                .limit(CANDIDATE_LIMIT).map(user -> new CreditablePerson(user.userId(), displayNameOrFallback(user),
                        memberSet.contains(user.userId())))
                .toList();
    }

    /**
     * The community's roster, one page at a time, each membership carrying the member's display name.
     *
     * <p>
     * A member the directory has no name for - invited, never signed in - falls back to their user id rather than
     * vanishing from the roster.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommunityMember> list(long communityId, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var memberships = membershipLedger.page(organizationId, communityId, pageable);
        var displayNames = displayNames(organizationId,
                memberships.getContent().stream().map(MembershipRecord::getUserId).toList());

        return memberships.map(membership -> new CommunityMember(membership.getUserId(),
                displayNames.getOrDefault(membership.getUserId(), membership.getUserId())));
    }

    /**
     * The community's roster as user ids in display-name order, case-insensitively, tie-broken on the user id so two
     * members sharing a name hold a stable position across pages.
     *
     * <p>
     * Ordering by a column this module does not own costs the whole roster's ids and names to answer. That is bounded
     * by community size, which is a roster of practitioners rather than a tenant-wide user table, and only the
     * {@code display_name} sort pays it: {@code joined_at} keeps its own paged query.
     */
    @Transactional(readOnly = true)
    public List<String> rosterOrderedByName(long communityId) {

        var organizationId = organizationContext.organizationId();
        var memberIds = membershipLedger.memberUserIds(organizationId, communityId);
        if (memberIds.isEmpty()) {
            return List.of();
        }
        var names = displayNames(organizationId, memberIds);
        return memberIds.stream()
                .sorted(Comparator.<String, String>comparing(id -> normalize(names.getOrDefault(id, id)))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private Map<String, String> displayNames(String organizationId, List<String> userIds) {

        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userDirectory.byIds(organizationId, userIds).stream()
                .filter(user -> user.displayName() != null && !user.displayName().isBlank())
                .collect(Collectors.toMap(DirectoryUser::userId, DirectoryUser::displayName));
    }

    /** Same fallback as {@link #list}: a member the directory has no name for is shown by their user id, not blank. */
    private static String displayNameOrFallback(DirectoryUser user) {

        return user.displayName() != null && !user.displayName().isBlank() ? user.displayName() : user.userId();
    }

    static boolean matchesExactly(DirectoryUser user, String normalizedQuery) {

        return normalizedQuery.equals(normalize(user.displayName())) || normalizedQuery.equals(normalize(user.email()));
    }

    static String normalize(String value) {

        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
