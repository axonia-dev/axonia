package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MembershipFilter;
import dev.axonia.community.api.TopicManagementPolicy;
import dev.axonia.community.application.port.out.CommunitySearch;
import dev.axonia.community.domain.CommunityRecord;
import dev.axonia.community.domain.KindCommunityCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcCommunityStoreTest {

    private static final CommunityRecord STORED = CommunityRecord.builder().id(7L).organizationId("default")
            .slug("backend-guild").name("Backend Guild").kindId(3L).description("A guild")
            .status(CommunityStatus.ARCHIVED).badgeKey("badge").heroKey("hero").accentColor("#1AA6A0").heroFocusX(10)
            .heroFocusY(20).badgeFocusX(30).badgeFocusY(40).canStartSeries(true).canEditStructure(true)
            .canQueueTopics(true).canManageSchedule(true).canEditCommunity(true).canManageLookAndFeel(true)
            .canManageMembers(true).canManageContributionPolicy(true).canArchiveCommunity(true).canCurateTags(true)
            .canViewHealth(true).canInviteMembers(true).canManageCommunication(true).canCreditContributions(true)
            .notifySessionUpcoming(false).notifySessionWrapUp(false).sessionUpcomingLeadDays(5).wrapUpGraceHours(12)
            .topicManagementPolicy(TopicManagementPolicy.ANY_MEMBER).createdAt(Instant.parse("2026-06-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-02T00:00:00Z")).version(4L).build();

    @Mock
    private CommunityRepository communityRepository;

    @InjectMocks
    private JdbcCommunityStore sut;

    @Test
    void findBySlug_maps_every_column() {

        // given
        when(communityRepository.findByOrganizationIdAndSlug("default", "backend-guild"))
                .thenReturn(Optional.of(JdbcCommunityStore.toEntity(STORED)));

        // when
        var actual = sut.findBySlug("default", "backend-guild");

        // then
        assertThat(actual).contains(STORED);
    }

    @Test
    void findById_maps_every_column() {

        // given
        when(communityRepository.findByOrganizationIdAndId("default", 7L))
                .thenReturn(Optional.of(JdbcCommunityStore.toEntity(STORED)));

        // when
        var actual = sut.findById("default", 7L);

        // then
        assertThat(actual).contains(STORED);
    }

    @Test
    void findByIds_maps_each_row() {

        // given
        when(communityRepository.findByOrganizationIdAndIdIn("default", List.of(7L)))
                .thenReturn(List.of(JdbcCommunityStore.toEntity(STORED)));

        // when
        var actual = sut.findByIds("default", List.of(7L));

        // then
        assertThat(actual).containsExactly(STORED);
    }

    @Test
    void slugTaken_asks_the_repository() {

        // given
        when(communityRepository.existsByOrganizationIdAndSlug("default", "backend-guild")).thenReturn(true);

        // then
        assertThat(sut.slugTaken("default", "backend-guild")).isTrue();
    }

    @Test
    void searchByName_maps_the_page() {

        // given
        var pageable = PageRequest.of(0, 20);
        when(communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCase("default",
                CommunityStatus.ACTIVE, "guild", pageable))
                .thenReturn(new PageImpl<>(List.of(JdbcCommunityStore.toEntity(STORED)), pageable, 1));

        // when
        var actual = sut.searchByName("default", CommunityStatus.ACTIVE, "guild", pageable);

        // then
        assertThat(actual.getContent()).containsExactly(STORED);
    }

    @Test
    void search_unfiltered_reads_every_community() {

        // given
        var pageable = PageRequest.of(0, 20);
        when(communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCase("default",
                CommunityStatus.ACTIVE, "guild", pageable))
                .thenReturn(new PageImpl<>(List.of(JdbcCommunityStore.toEntity(STORED)), pageable, 1));

        // when
        var actual = sut.search(search(null, Set.of(7L)), pageable);

        // then
        assertThat(actual.getContent()).containsExactly(STORED);
    }

    @Test
    void search_joined_reads_among_the_memberships() {

        // given
        var pageable = PageRequest.of(0, 20);
        when(communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdIn("default",
                CommunityStatus.ACTIVE, "guild", Set.of(7L), pageable))
                .thenReturn(new PageImpl<>(List.of(JdbcCommunityStore.toEntity(STORED)), pageable, 1));

        // when
        var actual = sut.search(search(MembershipFilter.JOINED, Set.of(7L)), pageable);

        // then
        assertThat(actual.getContent()).containsExactly(STORED);
    }

    @Test
    void search_joined_with_no_membership_is_empty_without_a_query() {

        // given
        var pageable = PageRequest.of(0, 20);

        // when
        var actual = sut.search(search(MembershipFilter.JOINED, Set.of()), pageable);

        // then
        assertThat(actual.getContent()).isEmpty();
        assertThat(actual.getTotalElements()).isZero();
        verifyNoInteractions(communityRepository);
    }

    @Test
    void search_explore_with_no_membership_reads_every_community() {

        // given
        var pageable = PageRequest.of(0, 20);
        when(communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCase("default",
                CommunityStatus.ACTIVE, "guild", pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        sut.search(search(MembershipFilter.EXPLORE, Set.of()), pageable);

        // then
        verify(communityRepository).findByOrganizationIdAndStatusAndNameContainingIgnoreCase("default",
                CommunityStatus.ACTIVE, "guild", pageable);
    }

    @Test
    void search_explore_excludes_the_memberships() {

        // given
        var pageable = PageRequest.of(0, 20);
        when(communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdNotIn("default",
                CommunityStatus.ACTIVE, "guild", Set.of(7L), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        sut.search(search(MembershipFilter.EXPLORE, Set.of(7L)), pageable);

        // then
        verify(communityRepository).findByOrganizationIdAndStatusAndNameContainingIgnoreCaseAndIdNotIn("default",
                CommunityStatus.ACTIVE, "guild", Set.of(7L), pageable);
    }

    private static CommunitySearch search(MembershipFilter membership, Set<Long> memberOf) {

        return CommunitySearch.builder().organizationId("default").status(CommunityStatus.ACTIVE).name("guild")
                .membership(membership).memberOf(memberOf).build();
    }

    @Test
    void countOfKind_asks_the_repository() {

        // given
        when(communityRepository.countByOrganizationIdAndKindId("default", 3L)).thenReturn(2L);

        // then
        assertThat(sut.countOfKind("default", 3L)).isEqualTo(2L);
    }

    @Test
    void reassignKind_delegates_to_the_repository() {

        // when
        sut.reassignKind("default", 3L, 4L);

        // then
        verify(communityRepository).reassignKind("default", 3L, 4L);
    }

    @Test
    void countByKind_maps_each_row() {

        // given
        when(communityRepository.countCommunitiesByKind("default"))
                .thenReturn(List.of(new CommunityRepository.KindCommunityCount(3L, 5L)));

        // then
        assertThat(sut.countByKind("default")).containsExactly(new KindCommunityCount(3L, 5L));
    }

    @Test
    void save_writes_every_column_and_returns_what_was_stored() {

        // given
        when(communityRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.save(STORED);

        // then
        var captor = ArgumentCaptor.forClass(CommunityEntity.class);
        verify(communityRepository).save(captor.capture());
        assertThat(JdbcCommunityStore.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }
}
