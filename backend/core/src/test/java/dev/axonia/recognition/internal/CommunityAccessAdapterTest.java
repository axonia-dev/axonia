package dev.axonia.recognition.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.NotFoundException;
import dev.axonia.community.Community;
import dev.axonia.community.CommunityService;
import dev.axonia.community.MembershipService;
import dev.axonia.recognition.CommunitySummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAccessAdapterTest {

    @Mock
    private CommunityService communityService;

    @Mock
    private MembershipService membershipService;

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
    void summaryById_mapsEachDistinctIdToItsSlugAndName() {

        // given
        when(communityService.getById(7L))
                .thenReturn(Community.builder().id(7L).slug("frontend-guild").name("Frontend Guild").build());
        when(communityService.getById(9L))
                .thenReturn(Community.builder().id(9L).slug("kafka-crew").name("Kafka Crew").build());

        // then
        assertThat(sut.summaryById(List.of(7L, 9L, 7L))).containsOnly(
                entry(7L, new CommunitySummary("frontend-guild", "Frontend Guild")),
                entry(9L, new CommunitySummary("kafka-crew", "Kafka Crew")));
    }

    @Test
    void summaryById_skipsIdsThatNoLongerResolve() {

        // given
        when(communityService.getById(7L))
                .thenReturn(Community.builder().id(7L).slug("frontend-guild").name("Frontend Guild").build());
        when(communityService.getById(9L)).thenThrow(new NotFoundException("gone"));

        // then
        assertThat(sut.summaryById(List.of(7L, 9L)))
                .containsOnly(entry(7L, new CommunitySummary("frontend-guild", "Frontend Guild")));
    }
}
