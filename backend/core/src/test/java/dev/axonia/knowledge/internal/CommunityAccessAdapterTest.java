package dev.axonia.knowledge.internal;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.Community;
import dev.axonia.community.CommunityCapabilities;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.MemberMatch;
import dev.axonia.community.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAccessAdapterTest {

    @Mock
    private CommunityService communityService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private MemberDirectory memberDirectory;

    @Mock
    private CommunityPolicyService communityPolicyService;

    @Mock
    private CommunityCapabilities communityCapabilities;

    @InjectMocks
    private CommunityAccessAdapter sut;

    @Test
    void communityIdForSlug_delegatesToCommunityService() {

        // given
        when(communityService.getBySlug("frontend-guild"))
                .thenReturn(Community.builder().id(7L).slug("frontend-guild").build());

        // then
        assertThat(sut.communityIdForSlug("frontend-guild")).isEqualTo(7L);
    }

    @Test
    void isMember_delegatesToMembershipService() {

        // given
        when(membershipService.isMember(7L, "dev-user")).thenReturn(true);

        // then
        assertThat(sut.isMember(7L, "dev-user")).isTrue();
    }

    @Test
    void isOrganizer_delegatesToMembershipService() {

        // given
        when(membershipService.isOrganizer(7L, "dev-user")).thenReturn(false);

        // then
        assertThat(sut.isOrganizer(7L, "dev-user")).isFalse();
    }

    @Test
    void contributionPolicy_delegatesToCommunityPolicyService() {

        // given
        var policy = new ContributionPolicy(true, false, true, false);
        when(communityPolicyService.getContributionPolicyById(7L)).thenReturn(policy);

        // then
        assertThat(sut.contributionPolicy(7L)).isEqualTo(policy);
    }

    /**
     * The port carries no matching logic of its own: resolution belongs to {@code community}, which owns the roster. A
     * second copy of that rule here would be free to drift into crediting one person's work to another.
     */
    @Test
    void resolveMember_delegatesToTheMemberDirectory() {

        // given
        when(memberDirectory.resolve(7L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));

        // then
        assertThat(sut.resolveMember(7L, "Alice Dupont")).isEqualTo(new MemberMatch.Matched("alice-id"));
    }

    @Test
    void resolveMember_creditsNobodyWhenTheReferenceMatchesNoMember() {

        // given
        when(memberDirectory.resolve(7L, "J. Smith")).thenReturn(new MemberMatch.NotFound());

        // then
        assertThat(sut.resolveMember(7L, "J. Smith")).isEqualTo(new MemberMatch.NotFound());
    }

    @Test
    void canCreditContributions_delegatesToCommunityCapabilities() {

        // given
        when(communityCapabilities.canCreditContributions(7L, "dev-user")).thenReturn(true);

        // then
        assertThat(sut.canCreditContributions(7L, "dev-user")).isTrue();
    }

    @Test
    void identifiesExactly_delegatesToTheMemberDirectory() {

        // given
        when(memberDirectory.identifiesExactly("alice-id", "Alice Dupont")).thenReturn(true);

        // then
        assertThat(sut.identifiesExactly("alice-id", "Alice Dupont")).isTrue();
    }
}
