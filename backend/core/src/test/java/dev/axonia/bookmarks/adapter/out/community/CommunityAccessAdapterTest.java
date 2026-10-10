package dev.axonia.bookmarks.adapter.out.community;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.bookmarks.domain.CommunitySummary;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.Memberships;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAccessAdapterTest {

    @Mock
    private Communities communityService;

    @Mock
    private Memberships membershipService;

    @InjectMocks
    private CommunityAccessAdapter sut;

    @Test
    void isMember_delegatesToMembershipService() {

        // given
        when(membershipService.isMember(7L, "dev-user")).thenReturn(true);

        // then
        assertThat(sut.isMember(7L, "dev-user")).isTrue();
    }

    @Test
    void summaryById_mapsResolvedCommunityToSummary() {

        // given
        when(communityService.getById(7L)).thenReturn(Community.builder().id(7L).slug("frontend-guild")
                .name("Frontend Guild").accentColor("#336699").build());

        // then
        assertThat(sut.summaryById(7L))
                .isEqualTo(new CommunitySummary(7L, "frontend-guild", "Frontend Guild", "#336699"));
    }

    @Test
    void summaryById_returnsNullWhenCommunityNotFound() {

        // given
        when(communityService.getById(7L)).thenThrow(new NotFoundException("community 7 not found"));

        // then
        assertThat(sut.summaryById(7L)).isNull();
    }
}
