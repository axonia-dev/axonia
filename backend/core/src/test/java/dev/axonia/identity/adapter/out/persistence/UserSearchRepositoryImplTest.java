package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.domain.UserSearch;
import dev.axonia.identity.domain.UserUpsert;

import static org.assertj.core.api.Assertions.assertThat;

class UserSearchRepositoryImplTest extends CoreJdbcTestSupport {

    // user_id values on purpose do not sort the same way as the display names: "default"/organization_id is backed
    // by the (organization_id, user_id) unique index, so Postgres answers these queries with an index scan ordered
    // by user_id whenever there is no ORDER BY to override it. If the ids sorted anna/gabriel/namesake/zoe like the
    // display names do, that coincidence alone would make the result look correctly ordered even with the ORDER BY
    // deleted, on top of the insertion-order coincidence a small seq-scanned table would give. Decoupling id order
    // from name order closes both loopholes at once.
    private static final String ZOE = "user-b-zoe";
    private static final String GABRIEL = "user-c-gabriel";
    private static final String NAMESAKE = "user-d-namesake";
    private static final String ANNA = "user-z-anna";
    private static final String CROSS_ORG = "cross-org";
    private static final String BACKSLASH = "backslash";

    /**
     * A restriction covering every user seeded in the default organization. Passed to {@link #search} where a test
     * wants the token match unconstrained by the RESTRICTED-mode narrowing; the whole-org mode itself is
     * {@code restrictToUserIds = null}, exercised separately via {@link #searchOrganization}.
     */
    private static final List<String> EVERYONE = List.of(ANNA, GABRIEL, NAMESAKE, ZOE);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setUp() {

        // Seeded in an order that contradicts "ORDER BY display_name, user_id" too, so a heap/seq scan without that
        // ORDER BY would also come back wrong, not just an index scan (see the id-naming comment above). Anna's and
        // Zoe's emails are likewise prefixed out of alphabetical name order: organization_id is also the leftmost
        // column of idx_identity_user_organization_email, so a missing ORDER BY can be satisfied by a scan of that
        // index
        // too, and if the email order happened to match the name order that would be a third silent loophole.
        upsert("default", ZOE, "Zoe Zimmer", "a1-zoe@acme.com");
        // a second, genuine Gabriel Dinant in the same organization: the reason a name search must be restrictable
        upsert("default", NAMESAKE, "Gabriel Dinant", "gabriel.dinant@other.example");
        upsert("default", GABRIEL, "Gabriel Dinant", "gabriel.dinant@acme.com");
        upsert("default", ANNA, "Anna Dinant", "z9-anna@acme.com");
        // the same person's name again, in another tenant
        upsert("acme", CROSS_ORG, "Gabriel Dinant", "gabriel.dinant@acme.com");
    }

    @Test
    void searchExcludesDeactivatedUsersUnlessAskedForThem() {

        seedUser("here", "Nora Present", true);
        seedUser("gone", "Nora Departed", false);

        var picker = userRepository
                .search(UserSearch.builder().organizationId("default").query("Nora").limit(10).build());
        var roster = userRepository.search(
                UserSearch.builder().organizationId("default").query("Nora").limit(10).includeDeparted(true).build());

        assertThat(picker).extracting(UserEntity::getUserId).containsExactly("here");
        assertThat(roster).extracting(UserEntity::getUserId).containsExactlyInAnyOrder("here", "gone");
    }

    @Test
    void findsAMemberByTwoNameTokensInAnyOrder() {

        // when
        var result = search("dinant gabriel", EVERYONE, 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL, NAMESAKE);
    }

    @Test
    void findsAMemberByAReversedCommaSeparatedName() {

        // when
        var result = search("Dinant, Gabriel", List.of(GABRIEL), 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL);
    }

    @Test
    void findsAMemberByANameCarryingAMiddleInitial() {

        // when: the spelling a directory export that keeps middle initials produces
        var result = search("Gabriel J. Dinant", EVERYONE, 10);

        // then: the initial is dropped rather than required, so this is not a false NOT_FOUND
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL, NAMESAKE);
    }

    @Test
    void findsAMemberByAnInitialAndASurname() {

        // when
        var result = search("J. Dinant", List.of(GABRIEL), 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL);
    }

    @Test
    void findsNobodyForAReferenceThatIsNothingButInitials() {

        // when: no name token survives, so the initials are matched as written rather than dropped into a query that
        // would match the whole roster
        var result = search("J. D.", EVERYONE, 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void stillFiltersOnASingleLetter() {

        // when: a one-letter query is a prefix search, not an initial to discard - the admin roster box types this on
        // the first keystroke, and it must narrow rather than return everybody
        var result = search("z", EVERYONE, 10);

        // then: only Zoe Zimmer and Anna (z9-anna@acme.com) carry a "z"
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(ANNA, ZOE);
    }

    @Test
    void findsAMemberByTheirEmail() {

        // when
        var result = search("gabriel.dinant@acme.com", EVERYONE, 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL);
    }

    /**
     * Fragments of Gabriel's address, never of his display name "Gabriel Dinant" - the queries
     * {@link #nameOnlyModeNeverMatchesByEmail} reuses under {@code nameOnly}, so the two tests together prove the flag
     * is what changes the outcome, not a difference in what was searched for.
     */
    @Test
    void findsAMemberByAnEmailFragmentByDefault() {

        // when
        var result = search("dinant@acme", EVERYONE, 10);
        var localPartResult = search("l.din", EVERYONE, 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL);
        assertThat(localPartResult).extracting(UserEntity::getUserId).containsExactly(GABRIEL, NAMESAKE);
    }

    /**
     * The privacy rule a community's member picker depends on: {@code nameOnly} drops email from the haystack entirely,
     * so the same fragments that {@link #findsAMemberByAnEmailFragmentByDefault} shows finding Gabriel find nobody once
     * {@code nameOnly} is set.
     */
    @Test
    void nameOnlyModeNeverMatchesByEmail() {

        // when: "dinant@acme" spans the local part and the domain; "l.din" lies inside the local part alone
        var result = userRepository.search(UserSearch.builder().organizationId("default").query("dinant@acme")
                .restrictToUserIds(EVERYONE).limit(10).offset(0).nameOnly(true).build());
        var localPartResult = userRepository.search(UserSearch.builder().organizationId("default").query("l.din")
                .restrictToUserIds(EVERYONE).limit(10).offset(0).nameOnly(true).build());

        // then
        assertThat(result).isEmpty();
        assertThat(localPartResult).isEmpty();
    }

    @Test
    void nameOnlyModeStillMatchesByDisplayName() {

        // when
        var result = userRepository.search(UserSearch.builder().organizationId("default").query("dinant gabriel")
                .restrictToUserIds(EVERYONE).limit(10).offset(0).nameOnly(true).build());

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL, NAMESAKE);
    }

    @Test
    void findsAMemberByAnAngleBracketedMailboxHeader() {

        // when
        var result = search("Gabriel Dinant <gabriel.dinant@acme.com>", EVERYONE, 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL);
    }

    @Test
    void doesNotMatchWhenOnlyOneTokenMatches() {

        // when
        var result = search("gabriel dupont", EVERYONE, 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void treatsPercentAsALiteral() {

        // when
        var result = search("%", EVERYONE, 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void treatsUnderscoreAsALiteral() {

        // when
        var result = search("_", EVERYONE, 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void treatsABackslashAsALiteral() {

        // given: a display name that itself contains a literal backslash, seeded only for this test so it cannot
        // change any other test's result count
        upsert("default", BACKSLASH, "Back\\Slash", "backslash@acme.com");

        // when
        var result = search("\\", List.of(BACKSLASH), 10);

        // then: the escaped backslash in the query matches the literal backslash in the display name
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(BACKSLASH);
    }

    @Test
    void restrictsToTheGivenUserIds() {

        // when
        var result = search("gabriel dinant", List.of(GABRIEL), 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL);
    }

    @Test
    void returnsNothingForAnEmptyIdSet() {

        // when
        var result = search("gabriel", List.of(), 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void returnsNothingForABlankQueryWhenRestricted() {

        // when
        var result = search("  ,;  ", EVERYONE, 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void ordersDeterministicallyAndRespectsTheLimit() {

        // when
        var result = search("dinant", EVERYONE, 2);

        // then: display_name then user_id, so Anna precedes both Gabriels and "gabriel" precedes "namesake"
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(ANNA, GABRIEL);
    }

    @Test
    void doesNotCrossOrganizations() {

        // when: the other tenant's Gabriel Dinant, asked for by id, inside the default tenant
        var result = search("gabriel dinant", List.of(CROSS_ORG), 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findsTheOtherOrganizationsUserUnderItsOwnOrganizationId() {

        // when
        var result = userRepository.search(UserSearch.builder().organizationId("acme").query("gabriel dinant")
                .restrictToUserIds(List.of(CROSS_ORG)).limit(10).offset(0).build());

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(CROSS_ORG);
    }

    @Test
    void usesAtMostFiveTokens() {

        // when: a sixth token that matches nobody is truncated away rather than rejecting the query
        var result = search("gabriel dinant gabriel dinant gabriel zzznope", EVERYONE, 10);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL, NAMESAKE);
    }

    @Test
    void returnsTheWholeOrganizationForABlankUnrestrictedQuery() {

        // when
        var result = searchOrganization("", 10, 0);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(ANNA, GABRIEL, NAMESAKE, ZOE);
    }

    @Test
    void returnsTheWholeOrganizationForANullUnrestrictedQuery() {

        // when
        var result = searchOrganization(null, 10, 0);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(ANNA, GABRIEL, NAMESAKE, ZOE);
    }

    @Test
    void pagesAnUnrestrictedSearchByOffset() {

        // when
        var result = searchOrganization(null, 2, 2);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(NAMESAKE, ZOE);
    }

    @Test
    void tokenMatchesAnUnrestrictedSearchToo() {

        // when
        var result = searchOrganization("dinant gabriel", 10, 0);

        // then
        assertThat(result).extracting(UserEntity::getUserId).containsExactly(GABRIEL, NAMESAKE);
    }

    private List<UserEntity> search(String query, List<String> restrictToUserIds, int limit) {

        return userRepository.search(UserSearch.builder().organizationId("default").query(query)
                .restrictToUserIds(restrictToUserIds).limit(limit).offset(0).build());
    }

    private List<UserEntity> searchOrganization(String query, int limit, int offset) {

        return userRepository.search(
                UserSearch.builder().organizationId("default").query(query).limit(limit).offset(offset).build());
    }

    private void upsert(String organizationId, String userId, String displayName, String email) {

        userRepository.upsert(UserUpsert.builder().organizationId(organizationId).userId(userId)
                .displayName(displayName).email(email).scimId("scim-" + userId).build(),
                Instant.parse("2026-07-25T12:00:00Z"));
    }

    /**
     * {@code upsert} has no {@code active} parameter - it is a JIT sign-in write, and nobody signs in departed. A
     * departed member's row is written directly, the same way the SCIM deprovisioning path would leave it.
     */
    private void seedUser(String userId, String displayName, boolean active) {

        jdbc.update("""
                INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival,
                                           created_at, updated_at, version)
                VALUES (:org, :userId, :displayName, :userId || '@example.test', :active, 'scim-' || :userId,
                        'SYNCED', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', 0)
                """, Map.of("org", "default", "userId", userId, "displayName", displayName, "active", active));
    }
}
