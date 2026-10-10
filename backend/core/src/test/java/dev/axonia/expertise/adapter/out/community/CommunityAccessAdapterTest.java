package dev.axonia.expertise.adapter.out.community;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityCapabilities;
import dev.axonia.community.api.CommunityIdentity;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MemberCommunity;
import dev.axonia.community.api.Memberships;
import dev.axonia.expertise.domain.CommunityRef;
import dev.axonia.expertise.domain.CommunitySummary;
import dev.axonia.expertise.domain.DeclarableCommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAccessAdapterTest {

    @Mock
    private Communities communityService;

    @Mock
    private CommunityCapabilities communityCapabilities;

    @Mock
    private Memberships membershipService;

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
    void communityForSlug_saysWhetherTheCommunityIsArchived() {

        // given
        when(communityService.getBySlug("legacy"))
                .thenReturn(Community.builder().id(8L).slug("legacy").status(CommunityStatus.ARCHIVED).build());
        when(communityService.getBySlug("platform"))
                .thenReturn(Community.builder().id(7L).slug("platform").status(CommunityStatus.ACTIVE).build());

        // then
        assertThat(sut.communityForSlug("legacy")).isEqualTo(new CommunityRef(8L, true));
        assertThat(sut.communityForSlug("platform")).isEqualTo(new CommunityRef(7L, false));
    }

    @Test
    void summaryById_mapsEachDistinctIdToItsSlugAndName() {

        // given
        when(communityService.identitiesByIds(List.of(7L, 9L)))
                .thenReturn(Map.of(7L, new CommunityIdentity("frontend-guild", "Frontend Guild", null), 9L,
                        new CommunityIdentity("kafka-crew", "Kafka Crew", null)));

        // then
        assertThat(sut.summaryById(List.of(7L, 9L, 7L))).containsOnly(
                entry(7L, new CommunitySummary("frontend-guild", "Frontend Guild")),
                entry(9L, new CommunitySummary("kafka-crew", "Kafka Crew")));
    }

    @Test
    void summaryById_skipsIdsThatNoLongerResolve() {

        // given
        when(communityService.identitiesByIds(List.of(7L, 9L)))
                .thenReturn(Map.of(7L, new CommunityIdentity("frontend-guild", "Frontend Guild", null)));

        // then
        assertThat(sut.summaryById(List.of(7L, 9L)))
                .containsOnly(entry(7L, new CommunitySummary("frontend-guild", "Frontend Guild")));
    }

    @Test
    void declarableCommunities_mapsTheMembersActiveCommunities() {

        // given
        when(membershipService.activeCommunitiesOf("org-1", "dev-user"))
                .thenReturn(List.of(new MemberCommunity(9L, "apis", "APIs")));

        // then
        assertThat(sut.declarableCommunities("org-1", "dev-user"))
                .containsExactly(new DeclarableCommunity(9L, "apis", "APIs"));
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
    void canCurateTags_delegatesToCommunityCapabilities_true() {

        // given
        when(communityCapabilities.canCurateTags(7L, "dev-user")).thenReturn(true);

        // then
        assertThat(sut.canCurateTags(7L, "dev-user")).isTrue();
    }

    @Test
    void canCurateTags_delegatesToCommunityCapabilities_false() {

        // given
        when(communityCapabilities.canCurateTags(7L, "dev-user")).thenReturn(false);

        // then
        assertThat(sut.canCurateTags(7L, "dev-user")).isFalse();
    }
}
