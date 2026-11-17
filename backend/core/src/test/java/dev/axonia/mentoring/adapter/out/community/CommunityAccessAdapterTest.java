package dev.axonia.mentoring.adapter.out.community;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityIdentity;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.Memberships;
import dev.axonia.community.api.MentoringMemberships;
import dev.axonia.mentoring.domain.MentoringCommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAccessAdapterTest {

    @Mock
    private Communities communityService;
    @Mock
    private Memberships membershipService;
    @Mock
    private MentoringMemberships mentoringMemberships;

    @Test
    void communityForSlugCarriesTheArchivedAndMentoringFlags() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(communityService.getBySlug("legacy")).thenReturn(Community.builder().id(8L).slug("legacy").name("Legacy")
                .status(CommunityStatus.ARCHIVED).mentoringEnabled(false).build());
        when(communityService.getBySlug("platform")).thenReturn(Community.builder().id(7L).slug("platform")
                .name("Platform").status(CommunityStatus.ACTIVE).mentoringEnabled(true).build());

        // when / then
        assertThat(sut.communityForSlug("legacy"))
                .isEqualTo(new MentoringCommunity(8L, "legacy", "Legacy", true, false));
        assertThat(sut.communityForSlug("platform"))
                .isEqualTo(new MentoringCommunity(7L, "platform", "Platform", false, true));
    }

    @Test
    void communityByIdReadsTheSameFlags() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(communityService.getById(7L)).thenReturn(Community.builder().id(7L).slug("platform").name("Platform")
                .status(CommunityStatus.ACTIVE).mentoringEnabled(true).build());

        // when
        var actual = sut.communityById(7L);

        // then
        assertThat(actual).isEqualTo(new MentoringCommunity(7L, "platform", "Platform", false, true));
    }

    @Test
    void aGuildNameIsReadForTheGivenOrganization() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(communityService.identityById("org-1", 7L))
                .thenReturn(new CommunityIdentity("platform", "Platform", null));

        // when
        var actual = sut.nameOf("org-1", 7L);

        // then
        assertThat(actual).isEqualTo("Platform");
    }

    @Test
    void aMentoringGuildIsTheMembershipServicesOneQueryAnswer() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(mentoringMemberships.inAMentoringCommunity("org-1", "alice")).thenReturn(true);

        // when / then
        assertThat(sut.inAMentoringGuild("org-1", "alice")).isTrue();
        assertThat(sut.inAMentoringGuild("org-1", "bob")).isFalse();
    }

    @Test
    void communityIdsAreTheMembershipServicesAnswer() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(membershipService.communityIdsOf("org-1", "alice")).thenReturn(List.of(7L, 8L));

        // when
        var actual = sut.communityIdsOf("org-1", "alice");

        // then
        assertThat(actual).containsExactly(7L, 8L);
    }

    @Test
    void slugsAreReadOffTheIdentitiesInOneBatch() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(communityService.identitiesByIds(List.of(7L, 8L)))
                .thenReturn(Map.of(7L, new CommunityIdentity("platform", "Platform", null)));

        // when
        var actual = sut.slugsOf(List.of(7L, 8L));

        // then
        assertThat(actual).containsExactly(Map.entry(7L, "platform"));
    }

    @Test
    void membershipIsTheMembershipServicesAnswer() {

        // given
        var sut = new CommunityAccessAdapter(communityService, membershipService, mentoringMemberships);
        when(membershipService.isMember(7L, "alice")).thenReturn(true);
        when(membershipService.isMember("org-1", 7L, "alice")).thenReturn(true);

        // when / then
        assertThat(sut.isMember(7L, "alice")).isTrue();
        assertThat(sut.isMember("org-1", 7L, "alice")).isTrue();
        assertThat(sut.isMember(7L, "bob")).isFalse();
    }
}
