package dev.axonia.community;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.internal.MembershipEntity;
import dev.axonia.community.internal.MembershipRepository;
import dev.axonia.identity.UserDirectory;
import dev.axonia.identity.UserDirectory.DirectoryUser;
import lombok.RequiredArgsConstructor;

/**
 * Who the members of a community are called, and which of them a free-text reference means.
 *
 * <p>
 * The question needs both halves: membership, which this module owns, and names, which {@code identity} owns. Asking it
 * here is what lets the name search be narrowed to the roster <em>before</em> its limit applies, so a genuine match
 * cannot be pushed out of the results by namesakes who are not in the community.
 *
 * <p>
 * Exact-then-unique: an exact match on normalized display name or email wins outright; otherwise the directory's token
 * match is accepted only when exactly one member matches. Anything else resolves to nobody.
 */
@Service
@RequiredArgsConstructor
public class MemberDirectory {

    /**
     * Enough candidates to detect ambiguity without paging a large roster into memory.
     *
     * <p>
     * Public because an ambiguity count is a lower bound capped by exactly this number, and any surface that reports
     * one must say so with the real figure. A surface that hard-codes it silently starts lying the day it moves.
     */
    public static final int CANDIDATE_LIMIT = 25;

    private final MembershipRepository membershipRepository;
    private final UserDirectory userDirectory;
    private final OrganizationContext organizationContext;

    @Transactional(readOnly = true)
    public MemberMatch resolve(long communityId, String query) {

        if (query == null || query.isBlank()) {
            return new MemberMatch.NotFound();
        }
        var organizationId = organizationContext.organizationId();
        var reference = query.trim();

        // A caller that knows the id exactly - the UI, or an agent that has just read the roster - is honoured
        // directly, rather than made to round-trip a known id through a fuzzy search that could come back ambiguous.
        if (membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, reference)
                .isPresent()) {
            return new MemberMatch.Matched(reference);
        }

        var memberIds = membershipRepository.findUserIdsByOrganizationIdAndCommunityId(organizationId, communityId);
        var candidates = userDirectory.searchMembers(organizationId, memberIds, reference, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            return new MemberMatch.NotFound();
        }

        var normalized = normalize(reference);
        var exact = candidates.stream().filter(candidate -> matchesExactly(candidate, normalized)).toList();
        if (exact.size() == 1) {
            return new MemberMatch.Matched(exact.get(0).userId());
        }
        if (candidates.size() == 1) {
            return new MemberMatch.Matched(candidates.get(0).userId());
        }
        // Two or more exact matches (two members genuinely share a display name), or two or more token matches. Either
        // way we will not guess.
        return new MemberMatch.Ambiguous(exact.size() > 1 ? exact.size() : candidates.size());
    }

    /**
     * Whether a reference names this user exactly: their display name or email, normalized. Only the user's own
     * directory row is read, so the answer reveals nothing about anybody else.
     */
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
     * The members whose name matches a typed query, for a picker: at most {@link #CANDIDATE_LIMIT}, and never anybody
     * outside the community, because the directory is asked only about the roster. A blank query names nobody, so it
     * returns nothing rather than an arbitrary first page of people.
     *
     * <p>
     * Matched by display name only, via {@link UserDirectory#searchMembersByName}, never by email: a member could
     * otherwise probe another member's email address by trying fragments of it through the picker. {@link #resolve}
     * keeps matching email too, because the agent that calls it names people by email.
     */
    @Transactional(readOnly = true)
    public List<CommunityMember> search(long communityId, String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }
        var organizationId = organizationContext.organizationId();
        var memberIds = membershipRepository.findUserIdsByOrganizationIdAndCommunityId(organizationId, communityId);
        return userDirectory.searchMembersByName(organizationId, memberIds, query.trim(), CANDIDATE_LIMIT).stream()
                .map(user -> new CommunityMember(user.userId(), displayNameOrFallback(user))).toList();
    }

    /**
     * The community's roster, one page at a time, each membership carrying the member's display name.
     *
     * <p>
     * A member the directory has no name for - invited, never signed in - falls back to their user id rather than
     * vanishing from the roster.
     */
    @Transactional(readOnly = true)
    public Page<CommunityMember> list(long communityId, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var memberships = membershipRepository.findByOrganizationIdAndCommunityId(organizationId, communityId,
                pageable);
        var displayNames = displayNames(organizationId,
                memberships.getContent().stream().map(MembershipEntity::getUserId).toList());

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
        var memberIds = membershipRepository.findUserIdsByOrganizationIdAndCommunityId(organizationId, communityId);
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

    private static boolean matchesExactly(DirectoryUser user, String normalizedQuery) {

        return normalizedQuery.equals(normalize(user.displayName())) || normalizedQuery.equals(normalize(user.email()));
    }

    private static String normalize(String value) {

        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
