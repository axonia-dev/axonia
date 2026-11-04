package dev.axonia.community.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.community.api.CommunityMember;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CreditablePerson;
import dev.axonia.community.api.MemberMatch;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.MembershipRecord;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberDirectoryServiceTest {

    private static final String ORG = "acme";
    private static final long COMMUNITY_ID = 7L;
    private static final int CANDIDATE_LIMIT = 25;

    @Mock
    private MembershipLedger membershipLedger;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private OrganizationContext organizationContext;

    @InjectMocks
    private MemberDirectoryService memberDirectory;

    @BeforeEach
    void nobodyIsAMemberByDefault() {

        given(organizationContext.organizationId()).willReturn(ORG);
        given(membershipLedger.find(any(), any(), any())).willReturn(Optional.empty());
        given(membershipLedger.memberUserIds(ORG, COMMUNITY_ID)).willReturn(List.of());
    }

    private static DirectoryUser user(String id, String displayName, String email) {

        return new DirectoryUser(id, displayName, email, Instant.EPOCH, Instant.EPOCH,
                UserDirectory.MemberStatus.PRESENT);
    }

    private static MembershipRecord membership(String userId) {

        return MembershipRecord.builder().organizationId(ORG).communityId(COMMUNITY_ID).userId(userId)
                .role(CommunityRole.MEMBER).build();
    }

    /**
     * The community's roster, and the candidates the directory hands back for a query against it, matched by name or
     * email - what {@link MemberDirectoryService#resolve} asks for. The directory is asked with the trimmed reference,
     * and only ever about the community's own members.
     */
    private void membersAre(String query, DirectoryUser... members) {

        var memberIds = List.of(members).stream().map(DirectoryUser::userId).toList();
        given(membershipLedger.memberUserIds(ORG, COMMUNITY_ID)).willReturn(memberIds);
        given(userDirectory.searchMembers(ORG, memberIds, query.trim(), CANDIDATE_LIMIT)).willReturn(List.of(members));
    }

    /** The whole organization's candidates for a reference, matched by name or email, members among them. */
    private void organizationMatches(String query, DirectoryUser... people) {

        given(userDirectory.searchPeople(ORG, query.trim(), CANDIDATE_LIMIT)).willReturn(List.of(people));
    }

    /**
     * Same shape as {@link #membersAre}, but stubs the name-only port method {@link MemberDirectoryService#search}
     * calls - never the one that also matches email.
     */
    private void membersMatchByName(String query, DirectoryUser... members) {

        var memberIds = List.of(members).stream().map(DirectoryUser::userId).toList();
        given(membershipLedger.memberUserIds(ORG, COMMUNITY_ID)).willReturn(memberIds);
        given(userDirectory.searchMembersByName(ORG, memberIds, query.trim(), CANDIDATE_LIMIT))
                .willReturn(List.of(members));
    }

    @Test
    void resolvesAUserIdThatBelongsToAMember() {

        given(membershipLedger.find(ORG, COMMUNITY_ID, "u9")).willReturn(Optional.of(membership("u9")));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "u9")).isEqualTo(new MemberMatch.Matched("u9"));
    }

    @Test
    void doesNotSearchTheDirectoryWhenTheQueryIsAlreadyAMemberId() {

        // The UI, and an agent that has just read the roster, know exactly who they mean. Round-tripping a known id
        // through a fuzzy search could hand back Ambiguous for somebody already pinned down.
        given(membershipLedger.find(ORG, COMMUNITY_ID, "u9")).willReturn(Optional.of(membership("u9")));

        memberDirectory.resolvePerson(COMMUNITY_ID, "u9");

        then(userDirectory).should(never()).searchMembers(any(), any(), any(), anyInt());
    }

    @Test
    void resolvesTheUserIdOfSomebodyPresentOutsideTheCommunity() {

        given(userDirectory.presentOf(ORG, List.of("u9"))).willReturn(Set.of("u9"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "u9")).isEqualTo(new MemberMatch.Matched("u9"));
        then(userDirectory).should(never()).searchPeople(any(), any(), anyInt());
    }

    @Test
    void doesNotResolveTheUserIdOfSomebodyNoLongerPresent() {

        // Deactivated, erased or never here: presentOf answers nobody, and an id is not a name to search for.
        membersAre("u9");

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "u9")).isEqualTo(new MemberMatch.NotFound());
    }

    @Test
    void creditsSomebodyOutsideTheCommunityByName() {

        organizationMatches("Omar", user("u8", "Omar Outside", "omar@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Omar")).isEqualTo(new MemberMatch.Matched("u8"));
    }

    @Test
    void aMemberWhoAloneMatchesKeepsTheMatchOverNamesakesOutsideTheCommunity() {

        var member = user("u1", "Alex Member", "alex.m@corp.com");
        membersAre("Alex", member);
        organizationMatches("Alex", member, user("u2", "Alex Other", "alex.o@corp.com"),
                user("u3", "Alex Third", "alex.t@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Alex")).isEqualTo(new MemberMatch.Matched("u1"));
    }

    @Test
    void anExactNameOutsideTheCommunityBeatsAMemberWhoOnlyPartlyMatches() {

        var member = user("u1", "Ann Leeds", "ann.leeds@corp.com");
        membersAre("Ann Lee", member);
        organizationMatches("Ann Lee", member, user("u2", "Ann Lee", "ann.lee@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Ann Lee")).isEqualTo(new MemberMatch.Matched("u2"));
    }

    @Test
    void ofTwoExactNamesakesTheMemberIsCredited() {

        var member = user("u1", "Sam Roe", "sam1@corp.com");
        membersAre("Sam Roe", member);
        organizationMatches("Sam Roe", member, user("u2", "Sam Roe", "sam2@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Sam Roe")).isEqualTo(new MemberMatch.Matched("u1"));
    }

    @Test
    void twoExactNamesakesOutsideTheCommunityAreAmbiguous() {

        organizationMatches("Sam Roe", user("u2", "Sam Roe", "sam2@corp.com"), user("u3", "Sam Roe", "sam3@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Sam Roe")).isEqualTo(new MemberMatch.Ambiguous(2));
    }

    @Test
    void severalPartialMatchesOutsideTheCommunityAreAmbiguous() {

        organizationMatches("Kim", user("u2", "Kim Park", "kim.p@corp.com"), user("u3", "Kim Lee", "kim.l@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Kim")).isEqualTo(new MemberMatch.Ambiguous(2));
    }

    @Test
    void resolvesAnExactDisplayNameCaseAndWhitespaceInsensitively() {

        membersAre("  ALICE DUPONT ", user("u1", "Alice Dupont", "alice@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "  ALICE DUPONT "))
                .isEqualTo(new MemberMatch.Matched("u1"));
    }

    @Test
    void resolvesAnExactEmail() {

        membersAre("ALICE@CORP.COM", user("u1", "Alice Dupont", "alice@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "ALICE@CORP.COM"))
                .isEqualTo(new MemberMatch.Matched("u1"));
    }

    @Test
    void prefersTheSingleExactMatchOverSeveralFuzzyOnes() {

        membersAre("Alice", user("u1", "Alice", "alice@corp.com"), user("u2", "Alice Dupont", "alice.d@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Alice")).isEqualTo(new MemberMatch.Matched("u1"));
    }

    @Test
    void resolvesTheOnlyFuzzyMatch() {

        membersAre("Dupont", user("u2", "Alice Dupont", "alice.d@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Dupont")).isEqualTo(new MemberMatch.Matched("u2"));
    }

    @Test
    void reportsAmbiguityWithItsCandidateCount() {

        membersAre("Bob", user("u3", "Bob Martin", "bob.m@corp.com"), user("u4", "Bob Stone", "bob.s@corp.com"),
                user("u5", "Bob Vance", "bob.v@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Bob")).isEqualTo(new MemberMatch.Ambiguous(3));
    }

    @Test
    void reportsAmbiguityWhenTwoMembersShareADisplayName() {

        membersAre("Bob Martin", user("u3", "Bob Martin", "bob3@corp.com"), user("u6", "Bob Martin", "bob6@corp.com"));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Bob Martin")).isEqualTo(new MemberMatch.Ambiguous(2));
    }

    @Test
    void returnsNotFoundForABlankQuery() {

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "   ")).isEqualTo(new MemberMatch.NotFound());
        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, null)).isEqualTo(new MemberMatch.NotFound());
        then(userDirectory).shouldHaveNoInteractions();
        then(membershipLedger).shouldHaveNoInteractions();
    }

    @Test
    void returnsNotFoundWhenNobodyMatches() {

        membersAre("Nobody");

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Nobody")).isEqualTo(new MemberMatch.NotFound());
    }

    @Test
    void toleratesAMemberWithNoEmail() {

        membersAre("Dave", user("u7", "David Example", null));

        assertThat(memberDirectory.resolvePerson(COMMUNITY_ID, "Dave")).isEqualTo(new MemberMatch.Matched("u7"));
    }

    @Test
    void searchListsTheMembersWhoseNameMatches() {

        membersMatchByName("ali", user("u1", "Alice Dupont", "alice@corp.com"),
                user("u2", "Alina Berg", "alina@corp.com"));

        assertThat(memberDirectory.searchPeople(COMMUNITY_ID, "  ali ")).containsExactly(
                new CreditablePerson("u1", "Alice Dupont", true), new CreditablePerson("u2", "Alina Berg", true));
    }

    @Test
    void searchNamesNobodyForABlankQuery() {

        assertThat(memberDirectory.searchPeople(COMMUNITY_ID, "   ")).isEmpty();
        then(userDirectory).should(never()).searchMembersByName(any(), any(), any(), anyInt());
        then(userDirectory).should(never()).searchPeopleByName(any(), any(), anyInt());
    }

    /**
     * The isolation the picker depends on: each community's search is scoped to that community's own roster, so a
     * member of another community can never surface as a candidate. Both communities share an organization and the
     * repository holds a distinct member set for each, and the assertions bind that distinction to what actually
     * reaches the directory.
     */
    @Test
    void searchScopesTheDirectoryQueryToEachCommunitysOwnMembers() {

        var otherCommunityId = 99L;
        given(membershipLedger.memberUserIds(ORG, otherCommunityId)).willReturn(List.of("outsider"));
        membersMatchByName("ali", user("u1", "Alice Dupont", "alice@corp.com"));

        memberDirectory.searchPeople(COMMUNITY_ID, "ali");
        memberDirectory.searchPeople(otherCommunityId, "ali");

        then(userDirectory).should().searchMembersByName(ORG, List.of("u1"), "ali", CANDIDATE_LIMIT);
        then(userDirectory).should().searchMembersByName(ORG, List.of("outsider"), "ali", CANDIDATE_LIMIT);
    }

    @Test
    void searchListsMembersFirstAndMarksWhoIsNotOne() {

        var alice = user("u1", "Alice Dupont", "alice@corp.com");
        membersMatchByName("ali", alice);
        given(userDirectory.searchPeopleByName(ORG, "ali", CANDIDATE_LIMIT))
                .willReturn(List.of(user("u2", "Alina Berg", "alina@corp.com"), alice));

        assertThat(memberDirectory.searchPeople(COMMUNITY_ID, "ali")).containsExactly(
                new CreditablePerson("u1", "Alice Dupont", true), new CreditablePerson("u2", "Alina Berg", false));
    }

    @Test
    void searchStopsAtTheCandidateLimit() {

        membersMatchByName("pat", user("m", "Pat Member", "pat@corp.com"));
        var outsiders = IntStream.range(0, CANDIDATE_LIMIT)
                .mapToObj(i -> user("o" + i, "Pat " + i, "pat" + i + "@corp.com")).toList();
        given(userDirectory.searchPeopleByName(ORG, "pat", CANDIDATE_LIMIT)).willReturn(outsiders);

        var actual = memberDirectory.searchPeople(COMMUNITY_ID, "pat");

        assertThat(actual).hasSize(CANDIDATE_LIMIT).first().isEqualTo(new CreditablePerson("m", "Pat Member", true));
    }

    /** Same fallback as {@link #listFallsBackToTheUserIdWhenTheDirectoryHasNoNameForThem}. */
    @Test
    void searchFallsBackToTheUserIdForABlankDisplayName() {

        membersMatchByName("ghost", user("ghost", "", "ghost@corp.com"));

        assertThat(memberDirectory.searchPeople(COMMUNITY_ID, "ghost"))
                .containsExactly(new CreditablePerson("ghost", "ghost", true));
    }

    @Test
    void theRosterIsOrderedByDisplayNameIgnoringCase() {

        given(membershipLedger.memberUserIds(ORG, COMMUNITY_ID)).willReturn(List.of("u-carl", "u-ana", "u-bea"));
        given(userDirectory.byIds(ORG, List.of("u-carl", "u-ana", "u-bea"))).willReturn(List.of(
                user("u-carl", "carl diaz", null), user("u-ana", "Ana Bello", null), user("u-bea", "Bea Ng", null)));

        var ordered = memberDirectory.rosterOrderedByName(COMMUNITY_ID);

        assertThat(ordered).containsExactly("u-ana", "u-bea", "u-carl");
    }

    /**
     * A member the directory has no name for is shown by their user id everywhere else, so they sort by it too rather
     * than dropping out of the roster.
     */
    @Test
    void aMemberWithNoDirectoryNameSortsUnderTheirUserId() {

        given(membershipLedger.memberUserIds(ORG, COMMUNITY_ID)).willReturn(List.of("zed", "u-ana"));
        given(userDirectory.byIds(ORG, List.of("zed", "u-ana"))).willReturn(List.of(user("u-ana", "Ana Bello", null)));

        var ordered = memberDirectory.rosterOrderedByName(COMMUNITY_ID);

        assertThat(ordered).containsExactly("u-ana", "zed");
    }

    /**
     * Two members genuinely sharing a display name must hold a stable order across pages, or a paged reader sees one of
     * them twice and the other never.
     */
    @Test
    void membersSharingANameTieBreakOnTheirUserId() {

        given(membershipLedger.memberUserIds(ORG, COMMUNITY_ID)).willReturn(List.of("u-b", "u-a"));
        given(userDirectory.byIds(ORG, List.of("u-b", "u-a")))
                .willReturn(List.of(user("u-b", "Sam Lee", null), user("u-a", "Sam Lee", null)));

        var ordered = memberDirectory.rosterOrderedByName(COMMUNITY_ID);

        assertThat(ordered).containsExactly("u-a", "u-b");
    }

    @Test
    void anEmptyRosterOrdersToNothing() {

        assertThat(memberDirectory.rosterOrderedByName(COMMUNITY_ID)).isEmpty();
        then(userDirectory).should(never()).byIds(any(), any());
    }

    @Test
    void listsMembersWithTheirDisplayNames() {

        givenAPageOf(membership("u1"), membership("u2"));
        given(userDirectory.byIds(ORG, List.of("u1", "u2"))).willReturn(
                List.of(user("u1", "Alice Dupont", "alice@corp.com"), user("u2", "Bob Martin", "bob@corp.com")));

        assertThat(memberDirectory.list(COMMUNITY_ID, PageRequest.of(0, 20)))
                .containsExactly(new CommunityMember("u1", "Alice Dupont"), new CommunityMember("u2", "Bob Martin"));
    }

    @Test
    void listFallsBackToTheUserIdWhenTheDirectoryHasNoNameForThem() {

        // Invited, never signed in: they have a membership but no identity_user row. Vanishing from the roster would
        // be worse than showing an id.
        givenAPageOf(membership("u1"), membership("ghost"));
        given(userDirectory.byIds(ORG, List.of("u1", "ghost")))
                .willReturn(List.of(user("u1", "Alice Dupont", "alice@corp.com")));

        assertThat(memberDirectory.list(COMMUNITY_ID, PageRequest.of(0, 20)))
                .containsExactly(new CommunityMember("u1", "Alice Dupont"), new CommunityMember("ghost", "ghost"));
    }

    @Test
    void listNeverReturnsAnEmail() {

        // An email is the field that turns a roster into a phishing list. CommunityMember carries no such field, and
        // this asserts the shape stays that way.
        assertThat(CommunityMember.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("userId", "displayName");
    }

    @Test
    void doesNotAskTheDirectoryForAnEmptyPageOfMembers() {

        givenAPageOf();

        assertThat(memberDirectory.list(COMMUNITY_ID, PageRequest.of(0, 20))).isEmpty();
        then(userDirectory).should(never()).byIds(any(), any());
    }

    private void givenAPageOf(MembershipRecord... members) {

        var pageable = PageRequest.of(0, 20);
        Page<MembershipRecord> page = new PageImpl<>(List.of(members), pageable, members.length);
        given(membershipLedger.page(ORG, COMMUNITY_ID, pageable)).willReturn(page);
    }

    @Test
    void identifiesAUserByTheirOwnDisplayNameOrEmailExactly() {

        given(userDirectory.byIds(ORG, List.of("u1")))
                .willReturn(List.of(user("u1", "Alice Dupont", "alice@corp.com")));

        assertThat(memberDirectory.identifiesExactly("u1", "  ALICE DUPONT ")).isTrue();
        assertThat(memberDirectory.identifiesExactly("u1", "Alice@Corp.com")).isTrue();
    }

    @Test
    void doesNotIdentifyAUserByAFragmentOfTheirName() {

        given(userDirectory.byIds(ORG, List.of("u1")))
                .willReturn(List.of(user("u1", "Alice Dupont", "alice@corp.com")));

        assertThat(memberDirectory.identifiesExactly("u1", "Alice")).isFalse();
        then(userDirectory).should(never()).searchMembers(any(), any(), any(), anyInt());
    }

    @Test
    void doesNotIdentifyAUserTheDirectoryDoesNotKnow() {

        given(userDirectory.byIds(ORG, List.of("u1"))).willReturn(List.of());

        assertThat(memberDirectory.identifiesExactly("u1", "Alice Dupont")).isFalse();
    }

    @Test
    void aBlankReferenceDoesNotMatchTheBlankNameAndEmailOfAUserWithNeither() {

        // Reached only if the blank guard goes: this user's missing name and email normalize to a blank match.
        given(userDirectory.byIds(ORG, List.of("u1"))).willReturn(List.of(user("u1", null, null)));

        assertThat(memberDirectory.identifiesExactly("u1", "  ")).isFalse();
    }
}
