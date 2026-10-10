package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.MemberCommunity;
import dev.axonia.community.domain.MembershipRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcMembershipLedgerTest {

    private static final MembershipRecord STORED = MembershipRecord.builder().id(9L).organizationId("default")
            .communityId(1L).userId("alice").role(CommunityRole.ORGANIZER)
            .createdAt(Instant.parse("2026-06-01T00:00:00Z")).updatedAt(Instant.parse("2026-06-02T00:00:00Z"))
            .version(3L).build();

    @Mock
    private MembershipRepository membershipRepository;

    @InjectMocks
    private JdbcMembershipLedger sut;

    @Test
    void find_maps_every_column() {

        // given
        when(membershipRepository.findByOrganizationIdAndCommunityIdAndUserId("default", 1L, "alice"))
                .thenReturn(Optional.of(JdbcMembershipLedger.toEntity(STORED)));

        // then
        assertThat(sut.find("default", 1L, "alice")).contains(STORED);
    }

    @Test
    void findAllOf_maps_each_row() {

        // given
        when(membershipRepository.findByOrganizationIdAndUserId("default", "alice"))
                .thenReturn(List.of(JdbcMembershipLedger.toEntity(STORED)));

        // then
        assertThat(sut.findAllOf("default", "alice")).containsExactly(STORED);
    }

    @Test
    void activeCommunitiesOf_passes_the_rows_through() {

        // given
        var community = new MemberCommunity(1L, "backend-guild", "Backend Guild");
        when(membershipRepository.findActiveCommunitiesOf("default", "alice")).thenReturn(List.of(community));

        // then
        assertThat(sut.activeCommunitiesOf("default", "alice")).containsExactly(community);
    }

    @Test
    void count_asks_the_repository() {

        // given
        when(membershipRepository.countByOrganizationIdAndCommunityId("default", 1L)).thenReturn(4L);

        // then
        assertThat(sut.count("default", 1L)).isEqualTo(4L);
    }

    @Test
    void countWithRole_asks_the_repository() {

        // given
        when(membershipRepository.countByOrganizationIdAndCommunityIdAndRole("default", 1L, CommunityRole.ORGANIZER))
                .thenReturn(2L);

        // then
        assertThat(sut.countWithRole("default", 1L, CommunityRole.ORGANIZER)).isEqualTo(2L);
    }

    @Test
    void page_maps_the_page() {

        // given
        var pageable = PageRequest.of(0, 10);
        when(membershipRepository.findByOrganizationIdAndCommunityId("default", 1L, pageable))
                .thenReturn(new PageImpl<>(List.of(JdbcMembershipLedger.toEntity(STORED)), pageable, 1));

        // then
        assertThat(sut.page("default", 1L, pageable).getContent()).containsExactly(STORED);
    }

    @Test
    void memberUserIds_asks_the_repository() {

        // given
        when(membershipRepository.findUserIdsByOrganizationIdAndCommunityId("default", 1L))
                .thenReturn(List.of("alice", "bob"));

        // then
        assertThat(sut.memberUserIds("default", 1L)).containsExactly("alice", "bob");
    }

    @Test
    void userIdsWithRole_asks_the_repository() {

        // given
        when(membershipRepository.findUserIdsByOrganizationIdAndCommunityIdAndRole("default", 1L,
                CommunityRole.ORGANIZER)).thenReturn(List.of("alice"));

        // then
        assertThat(sut.userIdsWithRole("default", 1L, CommunityRole.ORGANIZER)).containsExactly("alice");
    }

    @Test
    void findMembers_maps_each_row() {

        // given
        when(membershipRepository.findByOrganizationIdAndCommunityIdAndUserIdIn("default", 1L, List.of("alice")))
                .thenReturn(List.of(JdbcMembershipLedger.toEntity(STORED)));

        // then
        assertThat(sut.findMembers("default", 1L, List.of("alice"))).containsExactly(STORED);
    }

    @Test
    void save_writes_every_column_and_returns_what_was_stored() {

        // given
        when(membershipRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // when
        var actual = sut.save(STORED);

        // then
        var captor = ArgumentCaptor.forClass(MembershipEntity.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(JdbcMembershipLedger.toRecord(captor.getValue())).isEqualTo(STORED);
        assertThat(actual).isEqualTo(STORED);
    }

    @Test
    void delete_carries_id_and_version() {

        // when
        sut.delete(STORED);

        // then
        var captor = ArgumentCaptor.forClass(MembershipEntity.class);
        verify(membershipRepository).delete(captor.capture());
        assertThat(JdbcMembershipLedger.toRecord(captor.getValue())).isEqualTo(STORED);
    }
}
