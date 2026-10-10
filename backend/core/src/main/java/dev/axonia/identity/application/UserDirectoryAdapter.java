package dev.axonia.identity.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserSearch;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class UserDirectoryAdapter implements UserDirectory {

    private final UserStore userStore;

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryUser> searchByOrganization(DirectorySearch search) {

        Objects.requireNonNull(search.organizationId(), "organizationId must not be null");
        if (search.page() < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        if (search.size() < 1) {
            throw new IllegalArgumentException("Page size must not be less than one");
        }
        // long arithmetic: page and size are both caller-supplied ints, and their product must not silently wrap
        // before it reaches the OFFSET clause.
        var offset = (long) search.page() * search.size();
        return toDirectoryUsers(UserSearch.builder().organizationId(search.organizationId()).query(search.query())
                .limit(search.size()).offset(offset).includeDeparted(search.includeDeparted()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryUser> searchMembers(String organizationId, Collection<String> userIds, String query,
            int limit) {

        return toDirectoryUsers(memberSearch(organizationId, userIds, query, limit).build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryUser> searchMembersByName(String organizationId, Collection<String> userIds, String query,
            int limit) {

        return toDirectoryUsers(memberSearch(organizationId, userIds, query, limit).nameOnly(true).build());
    }

    // A null set would reach the repository as "no restriction" and widen a member search to the whole organization.
    private static UserSearch.UserSearchBuilder memberSearch(String organizationId, Collection<String> userIds,
            String query, int limit) {

        return UserSearch.builder().organizationId(organizationId).query(query)
                .restrictToUserIds(Objects.requireNonNullElse(userIds, List.of())).limit(limit).offset(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryUser> byIds(String organizationId, List<String> userIds) {

        // Total by construction: an id that resolves to no row is an erased member, not an absent result. Callers
        // render what they are given, so a subject that was rewritten to a surrogate can never come back as a blank.
        var found = userStore.findByOrganizationIdAndUserIdIn(organizationId, userIds).stream()
                .map(UserDirectoryAdapter::toDirectoryUser)
                .collect(Collectors.toMap(DirectoryUser::userId, Function.identity(), (first, second) -> first));
        return userIds.stream().distinct().map(id -> found.getOrDefault(id, erased(id))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> presentOf(String organizationId, Collection<String> userIds) {

        if (userIds.isEmpty()) {
            return Set.of();
        }
        return userStore.findByOrganizationIdAndUserIdIn(organizationId, List.copyOf(userIds)).stream()
                .map(UserDirectoryAdapter::toDirectoryUser).filter(user -> user.status() == MemberStatus.PRESENT)
                .map(DirectoryUser::userId).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> presentUserIds(String organizationId) {

        return userStore.findPresentUserIds(organizationId);
    }

    private static DirectoryUser erased(String userId) {

        return new DirectoryUser(userId, DirectoryUser.DELETED_MEMBER_LABEL, null, null, null, MemberStatus.ERASED);
    }

    private List<DirectoryUser> toDirectoryUsers(UserSearch search) {

        return userStore.search(search).stream().map(UserDirectoryAdapter::toDirectoryUser).toList();
    }

    /**
     * {@code lastActiveAt} reads {@code lastSeenAt}, never {@code updatedAt}: SCIM pushes and avatar syncs move the
     * latter, so it once reported a person who had never signed in as freshly active. A null here means not recorded,
     * which the console must render as such rather than as "never active".
     */
    static DirectoryUser toDirectoryUser(UserAccount entity) {

        return new DirectoryUser(entity.userId(), entity.displayName(), entity.email(), entity.createdAt(),
                entity.lastSeenAt(), UserDirectory.statusOf(entity.active()));
    }
}
