package dev.axonia.identity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import lombok.Builder;

public interface UserDirectory {

    /**
     * Users in an organization whose display name or email contains every token of the query (case-insensitive),
     * ordered by display name. A blank query returns all users in the organization, paginated.
     */
    List<DirectoryUser> searchByOrganization(DirectorySearch search);

    /**
     * The same token match, narrowed to a known set of users before the limit applies, so a genuine match cannot be
     * pushed out of the results by namesakes outside the set. At most {@code limit} users are returned, ordered
     * deterministically.
     *
     * <p>
     * An empty {@code userIds} matches nobody, as does a query with no usable tokens: a caller who names nobody is not
     * asking for everybody.
     */
    List<DirectoryUser> searchMembers(String organizationId, Collection<String> userIds, String query, int limit);

    /**
     * The same narrowed, capped match as {@link #searchMembers}, but against display name only - never email. A
     * community's member picker over HTTP calls this, not {@link #searchMembers}: a member could otherwise probe
     * another member's email address by trying fragments of it through the picker. The agent path that resolves a
     * free-text reference by email is the reason {@link #searchMembers} itself keeps matching both.
     */
    List<DirectoryUser> searchMembersByName(String organizationId, Collection<String> userIds, String query, int limit);

    /**
     * Resolve specific users of one organization by their ids (for joining onto role bindings). Scoped to the
     * organization: a user id belonging to another tenant resolves to nothing.
     */
    List<DirectoryUser> byIds(String organizationId, List<String> userIds);

    /**
     * Whether a person is still here. Derived, never stored: SCIM already maintains {@code identity_user.active}, and
     * an erasure deletes the row outright, so an unresolvable subject IS the erased signal.
     */
    enum MemberStatus {
        PRESENT, DEACTIVATED, ERASED
    }

    /**
     * The one formula for {@link MemberStatus#PRESENT} vs {@link MemberStatus#DEACTIVATED}, so every projection that
     * reads {@code identity_user.active} - this port's own adapter, and the HTTP-facing {@code User}/{@code
     * DirectoryUser} in this package - derives it the same way rather than each keeping its own copy of the ternary.
     * {@code null} reads as present: SCIM sets {@code active = false} explicitly on deprovisioning, so an unset flag
     * has never been touched.
     */
    static MemberStatus statusOf(Boolean active) {
        return Boolean.FALSE.equals(active) ? MemberStatus.DEACTIVATED : MemberStatus.PRESENT;
    }

    /**
     * The present members of the given set, in one query. Callers that aggregate over subjects (community health)
     * filter with this rather than joining, because they hold the ids already.
     *
     * <p>
     * It is also asked about a single id at a time, once per recipient at drain, by {@code RoundupSubjectPolicy}: an
     * organization-wide message has no membership to check other than still being in the directory. Worth knowing
     * before optimising this for the batch caller alone - the two call shapes are one id and a whole community's worth.
     */
    Set<String> presentOf(String organizationId, Collection<String> userIds);

    /**
     * Every present member of an organization, ids only. {@code byIds} and {@link #presentOf} both start from a known
     * set and a paged search is the wrong instrument for a fan-out, so an organization-wide sender asks for the whole
     * set once rather than walking pages it would immediately concatenate.
     *
     * <p>
     * Departed and erased subjects are excluded here rather than filtered by the caller: a recipient list is the one
     * place where including somebody who has left is a message actually sent, not merely a row.
     *
     * <p>
     * A caller with no JWT - the only kind that wants a whole organization at once - MUST run inside
     * {@code OrganizationScope.runAs(organizationId, ...)}, and that {@code runAs} MUST wrap the {@code @Transactional}
     * entry point rather than sit inside it: {@code identity_user} is row-level-secured on {@code app.current_org},
     * which Postgres chooses when the transaction acquires its connection. An unscoped thread leaves that setting
     * unset, and under the {@code axonia_app} role this method then answers an empty list instead of failing, which a
     * sender reads as an organization with nobody in it. No adapter test can catch it either: Testcontainers connects
     * as the table owner, and ownership bypasses the policy.
     */
    List<String> presentUserIds(String organizationId);

    record DirectoryUser(String userId, String displayName, String email, Instant joinedAt, Instant lastActiveAt,
            MemberStatus status) {

        /**
         * The label an erased subject renders under. Supplied by the backend rather than left null because
         * {@code displayName} is non-null for every consumer that sorts or maps by it.
         */
        public static final String DELETED_MEMBER_LABEL = "Deleted member";
    }

    /**
     * One {@link #searchByOrganization} request: a page of an organization's directory, optionally matched against
     * {@code query}.
     *
     * <p>
     * {@code includeDeparted} is a stated choice, not a default: false is a picker or suggestion, which must not offer
     * somebody who has left; true is an administrative roster, whose job is to show who exists.
     *
     * <p>
     * A record rather than five method parameters, which PMD would refuse.
     */
    @Builder
    record DirectorySearch(String organizationId, String query, int page, int size, boolean includeDeparted) {
    }
}
