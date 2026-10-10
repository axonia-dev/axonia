package dev.axonia.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.adapter.out.persistence.JdbcUserStore;
import dev.axonia.identity.adapter.out.persistence.UserEntityMother;
import dev.axonia.identity.adapter.out.persistence.UserRepository;
import dev.axonia.identity.api.UserDirectory.DirectorySearch;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.identity.api.UserDirectory.MemberStatus;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.identity.domain.UserUpsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@Import({UserDirectoryAdapter.class, JdbcUserStore.class})
class UserDirectoryAdapterTest extends CoreJdbcTestSupport {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";
    private static final String CAROL = "33333333-3333-3333-3333-333333333333";
    private static final Instant SEEN_AT = Instant.parse("2026-07-25T12:00:00Z");
    private static final String ORG = "default";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private UserDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {

        // default organization: Alice
        var alice = UserEntityMother.any().build();
        userRepository.upsert(
                UserUpsert.builder().organizationId(alice.getOrganizationId()).userId(alice.getUserId())
                        .displayName(alice.getDisplayName()).email(alice.getEmail()).scimId(alice.getScimId()).build(),
                SEEN_AT);

        // default organization: Bob. Email deliberately sorts before Alice's ("aaa-..." < "alice@..."):
        // organization_id is the leftmost column of idx_identity_user_organization_email too, so a query with no ORDER
        // BY
        // can be satisfied by a scan of that index, and if the email order happened to agree with the display-name
        // order, searchByTenant_pagesTheRoster below would pass whether or not the production ORDER BY exists.
        userRepository.upsert(UserUpsert.builder().organizationId("default").userId(BOB).displayName("Bob Brown")
                .email("aaa-bob@axonia.example").scimId(BOB).build(), SEEN_AT);

        // other organization: Carol
        userRepository.upsert(UserUpsert.builder().organizationId("acme").userId(CAROL).displayName("Carol Chen")
                .email("carol@acme.dev").scimId(CAROL).build(), SEEN_AT);
    }

    @Test
    void searchByTenant_blankQuery_returnsAllTenantUsers() {

        // when
        var result = searchByOrganization("", 0, 10, false);

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void searchByTenant_blankQuery_respectsTenantIsolation() {

        // when
        var result = searchByOrganization("", 0, 10, false);

        // then
        assertThat(result).extracting(DirectoryUser::userId).doesNotContain(CAROL);
    }

    @Test
    void searchByTenant_queryMatchesDisplayName_returnsMatchingUser() {

        // when
        var result = searchByOrganization("alice", 0, 10, false);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().userId()).isEqualTo(ALICE);
        assertThat(result.getFirst().displayName()).isEqualTo("Alice Anderson");
    }

    @Test
    void searchByTenant_queryMatchesEmail_returnsMatchingUser() {

        // when
        var result = searchByOrganization("bob@axonia", 0, 10, false);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().userId()).isEqualTo(BOB);
    }

    @Test
    void searchByTenant_queryMatchesNothing_returnsEmpty() {

        // when
        var result = searchByOrganization("zzznomatch", 0, 10, false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void searchByTenant_queryIsCaseInsensitive() {

        // when
        var result = searchByOrganization("ALICE", 0, 10, false);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().displayName()).isEqualTo("Alice Anderson");
    }

    @Test
    void searchByTenant_query_respectsTenantIsolation() {

        // when
        // "carol" exists in acme, not default
        var result = searchByOrganization("carol", 0, 10, false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void searchByTenant_matchesEveryTokenOfAFullName() {

        // when: the whole-substring search this replaced found nothing for a reversed name
        var result = searchByOrganization("Anderson, Alice", 0, 10, false);

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactly(ALICE);
    }

    @Test
    void searchByTenant_pagesTheRoster() {

        // when: ordered by display name, so Alice is page 0 and Bob is page 1
        var result = searchByOrganization("", 1, 1, false);

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactly(BOB);
    }

    @Test
    void searchByTenant_rejectsANegativePage() {

        // when/then: a negative page must not reach SQL as an OFFSET -N
        assertThatIllegalArgumentException().isThrownBy(() -> searchByOrganization("", -1, 10, false));
    }

    @Test
    void searchByTenant_rejectsAZeroSize() {

        // when/then: a size of zero must not silently return an empty page instead of failing
        assertThatIllegalArgumentException().isThrownBy(() -> searchByOrganization("", 0, 0, false));
    }

    @Test
    void searchByTenant_rejectsANullOrganizationId() {

        // when/then: the old positional signature made this uncompilable; the DirectorySearch builder makes it
        // constructible, so the adapter must refuse it at the boundary rather than silently querying
        // "WHERE organization_id = null" and returning zero rows.
        assertThatNullPointerException().isThrownBy(
                () -> adapter.searchByOrganization(DirectorySearch.builder().query("").page(0).size(10).build()));
    }

    @Test
    void searchByTenant_includeDepartedThreadsThroughToTheAdapter() {

        // given: a departed member the picker default must exclude and the roster flag must include - seeded
        // directly, the way a SCIM deprovisioning write leaves a row, rather than through the JIT upsert helper,
        // which has no active column to set.
        seedUser("departed-user", "Dana Departed", false);

        // when
        var picker = searchByOrganization("Dana", 0, 10, false);
        var roster = searchByOrganization("Dana", 0, 10, true);

        // then
        assertThat(picker).isEmpty();
        assertThat(roster).extracting(DirectoryUser::userId).containsExactly("departed-user");
    }

    @Test
    void searchMembers_narrowsToTheGivenUsers() {

        // when
        var result = adapter.searchMembers("default", List.of(ALICE), "a", 10);

        // then: Bob's email contains an "a" too, but he is not in the set
        assertThat(result).extracting(DirectoryUser::userId).containsExactly(ALICE);
    }

    @Test
    void searchMembers_matchesEveryTokenOfAFullName() {

        // when
        var result = adapter.searchMembers("default", List.of(ALICE, BOB), "Anderson, Alice", 10);

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactly(ALICE);
    }

    @Test
    void searchMembers_respectsTheLimit() {

        // when
        var result = adapter.searchMembers("default", List.of(ALICE, BOB), "axonia.example", 1);

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactly(ALICE);
    }

    @Test
    void searchMembers_emptyUserIds_matchesNobody() {

        // when
        var result = adapter.searchMembers("default", List.of(), "alice", 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void searchMembers_nullUserIds_matchesNobodyRatherThanTheWholeOrganization() {

        // when
        var result = adapter.searchMembers("default", null, "alice", 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void searchMembers_blankQuery_matchesNobody() {

        // when
        var result = adapter.searchMembers("default", List.of(ALICE, BOB), "  ", 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void searchMembers_respectsTenantIsolation() {

        // when: Carol's id, asked for inside the default organization
        var result = adapter.searchMembers("default", List.of(CAROL), "carol", 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void searchMembersByName_matchesDisplayName() {

        // when
        var result = adapter.searchMembersByName("default", List.of(ALICE, BOB), "alice", 10);

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactly(ALICE);
    }

    /**
     * The privacy rule the HTTP member picker depends on: a member could otherwise learn a namesake's email domain, or
     * confirm somebody's exact address, one fragment at a time. "aaa-bob" appears only in Bob's email, never his
     * display name, so a name-only match must find nobody - unlike {@link #searchMembers_respectsTheLimit}, which
     * proves the email-matching {@link #adapter}.searchMembers finds him by the same kind of fragment.
     */
    @Test
    void searchMembersByName_neverMatchesByEmail() {

        // when
        var result = adapter.searchMembersByName("default", List.of(ALICE, BOB), "aaa-bob", 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void byIds_returnsMatchingUsers() {

        // when
        var result = adapter.byIds("default", List.of(ALICE, BOB));

        // then
        assertThat(result).extracting(DirectoryUser::userId).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void byIds_doesNotReachIntoAnotherOrganization() {

        // when: Carol exists, but in acme, so from "default" she is unresolvable - the same signal as an erasure
        var result = adapter.byIds("default", List.of(ALICE, CAROL));

        // then: Carol's own organization never leaks her real name into a foreign tenant's lookup
        assertThat(result).extracting(u -> u.userId() + ":" + u.status()).containsExactlyInAnyOrder(ALICE + ":PRESENT",
                CAROL + ":ERASED");
    }

    @Test
    void byIds_unknownIdsResolveToAnErasedEntryRatherThanBeingOmitted() {

        // when: byIds is total - a subject nobody can resolve is the erased signal itself
        var result = adapter.byIds("default", List.of("no-such-id"));

        // then
        assertThat(result).singleElement().satisfies(u -> {
            assertThat(u.status()).isEqualTo(MemberStatus.ERASED);
            assertThat(u.displayName()).isEqualTo(DirectoryUser.DELETED_MEMBER_LABEL);
        });
    }

    @Test
    void searchByTenant_mapsTimestampsToJoinedAtAndLastActiveAt() {

        // given
        var alice = userRepository.findByOrganizationIdAndUserId("default", ALICE).orElseThrow();

        // when
        var result = searchByOrganization("alice", 0, 10, false);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().joinedAt()).isEqualTo(alice.getCreatedAt());
        assertThat(result.getFirst().lastActiveAt()).isEqualTo(alice.getLastSeenAt());
    }

    /**
     * The admin Users console presents lastActiveAt to customer administrators as how recently a person was active. It
     * used to read updated_at, which SCIM and avatar syncs also move, so a never-active person read as freshly active.
     * It must read last_seen_at, and report null - not a misleading timestamp - when that was never recorded.
     */
    @Test
    void lastActiveReadsLastSeenNotUpdatedAt() {

        // given
        var seenAt = Instant.parse("2026-03-01T10:00:00Z");
        var entity = UserAccount.builder().userId("u1").displayName("Ada").email("ada@acme.test")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z")).updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .lastSeenAt(seenAt).build();

        // when
        var actual = UserDirectoryAdapter.toDirectoryUser(entity);

        // then
        assertThat(actual.lastActiveAt()).isEqualTo(seenAt);
    }

    @Test
    void anUnrecordedLastSeenIsNullRatherThanTheUpdateTimestamp() {

        // given
        var entity = UserAccount.builder().userId("u1").displayName("Ada").email("ada@acme.test")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z")).updatedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .lastSeenAt(null).build();

        // when
        var actual = UserDirectoryAdapter.toDirectoryUser(entity);

        // then
        assertThat(actual.lastActiveAt()).isNull();
    }

    @Test
    void byIdsReturnsAnErasedEntryForASubjectThatResolvesToNothing() {

        seedUser("present-user", "Present Person", true);

        var users = adapter.byIds(ORG, List.of("present-user", "erased:0000"));

        assertThat(users).extracting(u -> u.userId() + ":" + u.status())
                .containsExactlyInAnyOrder("present-user:PRESENT", "erased:0000:ERASED");
        assertThat(users).filteredOn(u -> u.status() == MemberStatus.ERASED)
                .allMatch(u -> "Deleted member".equals(u.displayName()));
    }

    @Test
    void byIdsMarksADeprovisionedUserDeactivatedAndKeepsTheirName() {

        seedUser("gone-user", "Alice Martin", false);

        var users = adapter.byIds(ORG, List.of("gone-user"));

        assertThat(users).singleElement().satisfies(u -> {
            assertThat(u.status()).isEqualTo(MemberStatus.DEACTIVATED);
            assertThat(u.displayName()).isEqualTo("Alice Martin");
        });
    }

    @Test
    void presentOfKeepsOnlyActiveKnownSubjects() {

        seedUser("active-one", "Active One", true);
        seedUser("inactive-one", "Inactive One", false);

        assertThat(adapter.presentOf(ORG, List.of("active-one", "inactive-one", "erased:0000")))
                .containsExactly("active-one");
    }

    @Test
    void presentOfAnEmptySetIsEmptyWithoutQueryingTheRepository() {

        assertThat(adapter.presentOf(ORG, List.of())).isEmpty();
    }

    @Test
    void presentUserIdsGivesTheWholeOrganizationsRecipientList() {

        seedUser("active-one", "Active One", true);
        seedUser("inactive-one", "Inactive One", false);

        // Alice and Bob come from the JIT upsert in setUp, which writes no active column at all
        assertThat(adapter.presentUserIds(ORG)).containsExactlyInAnyOrder(ALICE, BOB, "active-one");
    }

    @Test
    void presentUserIdsSkipsAMemberWhoHasNeverSignedIn() {

        // given a SCIM-provisioned row with no subject yet: there is nobody to address
        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, NULL, 'Never Signed In', 'never@example.test', true, 'scim-never-signed-in', 'SYNCED',
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, Map.of("org", ORG));

        assertThat(adapter.presentUserIds(ORG)).doesNotContainNull().containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void presentUserIdsStopsAtTheOrganizationBoundary() {

        assertThat(adapter.presentUserIds(ORG)).doesNotContain(CAROL);
    }

    private List<DirectoryUser> searchByOrganization(String query, int page, int size, boolean includeDeparted) {

        return adapter.searchByOrganization(DirectorySearch.builder().organizationId("default").query(query).page(page)
                .size(size).includeDeparted(includeDeparted).build());
    }

    private void seedUser(String userId, String displayName, boolean active) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :displayName, :userId || '@example.test', :active, 'scim-' || :userId,
                        'SYNCED', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, Map.of("org", ORG, "userId", userId, "displayName", displayName, "active", active));
    }
}
