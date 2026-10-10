package dev.axonia.community.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import dev.axonia.access.api.OrganizationOwners;
import dev.axonia.community.api.CommunityMember;
import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.CommunityStatus;
import dev.axonia.community.api.MemberCommunity;
import dev.axonia.community.api.Membership;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.community.domain.MembershipRecord;
import dev.axonia.community.domain.MembershipRecordMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class MembershipServiceTest {

    @Mock
    private CommunityStore communityStore;

    @Mock
    private MembershipLedger membershipLedger;

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OrganizationOwners roleBindings;

    @Mock
    private MemberDirectoryService memberDirectory;

    @InjectMocks
    private MembershipService sut;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void join() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.empty());
        when(membershipLedger.save(any()))
                .thenAnswer(inv -> ((MembershipRecord) inv.getArgument(0)).toBuilder().id(9L).build());

        // when
        var actual = sut.join("backend-guild");

        // then
        assertThat(actual.userId()).isEqualTo("alice");
        assertThat(actual.role()).isEqualTo(CommunityRole.MEMBER);
    }

    @Test
    void join_archivedCommunityRejected() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).status(CommunityStatus.ARCHIVED).build()));

        // when / then
        assertThatThrownBy(() -> sut.join("backend-guild")).isInstanceOf(ConflictException.class);
    }

    @Test
    void join_alreadyMember() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(membershipLedger.find("default", 1L, "alice"))
                .thenReturn(Optional.of(MembershipRecordMother.any().build()));

        // when / then
        assertThatThrownBy(() -> sut.join("backend-guild")).isInstanceOf(ConflictException.class);
    }

    @Test
    void join_communityNotFound() {

        // given
        when(communityStore.findBySlug("default", "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.join("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void leave() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice").role(CommunityRole.MEMBER)
                .build();
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.of(member));

        // when
        sut.leave("backend-guild");

        // then
        verify(membershipLedger).delete(member);
    }

    @Test
    void leave_lastOrganizerCannotLeave() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice")
                .role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.of(organizer));
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.leave("backend-guild")).isInstanceOf(ConflictException.class);
        verify(membershipLedger, never()).delete(any());
    }

    @Test
    void leave_publishesCommunityMemberLeft() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice").role(CommunityRole.MEMBER)
                .build();
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.of(member));

        // when
        sut.leave("backend-guild");

        // then
        verify(eventPublisher).publishEvent(new CommunityMemberLeft("default", 1L, "alice"));
    }

    @Test
    void leave_lastOrganizer_publishesNothing() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice")
                .role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.of(organizer));
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.leave("backend-guild")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void leave_organizerWithAnotherOrganizerCanLeave() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice")
                .role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.of(organizer));
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(2L);

        // when
        sut.leave("backend-guild");

        // then
        verify(membershipLedger).delete(organizer);
    }

    @Test
    void leave_membershipNotFound() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(membershipLedger.find("default", 1L, "alice")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.leave("backend-guild")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void activeCommunitiesOf_returnsTheRepositorysRows() {

        // given
        var rows = List.of(new MemberCommunity(1L, "backend-guild", "Backend Guild"));
        when(membershipLedger.activeCommunitiesOf("acme", "alice")).thenReturn(rows);

        // when
        var actual = sut.activeCommunitiesOf("acme", "alice");

        // then
        assertThat(actual).isEqualTo(rows);
    }

    @Test
    void communityIdsOf_returnsEveryCommunityTheMemberBelongsTo() {

        // given
        var membership = MembershipRecordMother.any().communityId(1L).userId("alice").build();
        when(membershipLedger.findAllOf("acme-hub", "alice")).thenReturn(List.of(membership));

        // when
        var actual = sut.communityIdsOf("acme-hub", "alice");

        // then
        assertThat(actual).containsExactly(1L);
    }

    @Test
    void leaveForErasure_removesTheGivenMembersMembershipAndPublishesTheEvent() {

        // given - the erasure orchestrator acts on "alice" while some other subject may be the caller
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice").role(CommunityRole.MEMBER)
                .build();
        when(membershipLedger.find("acme-hub", 1L, "alice")).thenReturn(Optional.of(member));

        // when
        sut.leaveForErasure("acme-hub", 1L, "alice");

        // then
        verify(membershipLedger).delete(member);
        verify(eventPublisher).publishEvent(new CommunityMemberLeft("acme-hub", 1L, "alice"));
    }

    @Test
    void leaveForErasure_bypassesTheLastOrganizerGuard() {

        // given - alice is the sole organizer, which would refuse a voluntary leave(slug); erasure is not voluntary
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice")
                .role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("acme-hub", 1L, "alice")).thenReturn(Optional.of(organizer));

        // when
        sut.leaveForErasure("acme-hub", 1L, "alice");

        // then - it proceeds rather than throwing, and never even needs to count organizers to decide
        verify(membershipLedger).delete(organizer);
        verify(membershipLedger, never()).countWithRole(any(), any(), any());
        verify(eventPublisher).publishEvent(new CommunityMemberLeft("acme-hub", 1L, "alice"));
    }

    @Test
    void leaveForErasure_membershipNotFound() {

        // given
        when(membershipLedger.find("acme-hub", 1L, "alice")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.leaveForErasure("acme-hub", 1L, "alice")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void changeRole_promote() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("bob").role(CommunityRole.MEMBER)
                .build();
        when(membershipLedger.find("default", 1L, "bob")).thenReturn(Optional.of(member));
        when(membershipLedger.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.changeRole("backend-guild", "bob", CommunityRole.ORGANIZER);

        // then
        assertThat(actual.role()).isEqualTo(CommunityRole.ORGANIZER);
        verify(membershipAuthorizer).requireOrganizerOrOpenMember("default", 1L, "dev-user", false);
        verify(membershipAuthorizer).requireOrganizer("default", 1L, "dev-user");
    }

    /** The state leaveForErasure can strand, and the only one this hatch exists to repair. */
    @Test
    void changeRole_ownerCanPromoteInACommunityLeftWithNoOrganizerAtAll() {

        // given - the caller is the hub's Owner but holds no membership row in this community at all
        when(currentUser.userId()).thenReturn("owner-user");
        when(roleBindings.callerHoldsOwnerRole("default")).thenReturn(true);
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(0L);
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("bob").role(CommunityRole.MEMBER)
                .build();
        when(membershipLedger.find("default", 1L, "bob")).thenReturn(Optional.of(member));
        when(membershipLedger.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.changeRole("backend-guild", "bob", CommunityRole.ORGANIZER);

        // then - the repair hatch bypassed both authorization gates entirely
        assertThat(actual.role()).isEqualTo(CommunityRole.ORGANIZER);
        verifyNoInteractions(membershipAuthorizer);
    }

    /**
     * The hatch used to apply to any community, which let an Owner join a healthy one, promote themselves and demote
     * the incumbent organizers - a standing widening of an enterprise tenant's authorization model, not a repair.
     */
    @Test
    void changeRole_ownerGetsNoHatchInACommunityThatStillHasAnOrganizer() {

        // given
        when(currentUser.userId()).thenReturn("owner-user");
        when(roleBindings.callerHoldsOwnerRole("default")).thenReturn(true);
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(1L);
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        doThrow(new ForbiddenException("Organizer role required")).when(membershipAuthorizer)
                .requireOrganizerOrOpenMember("default", 1L, "owner-user", false);

        // when / then
        assertThatThrownBy(() -> sut.changeRole("backend-guild", "bob", CommunityRole.ORGANIZER))
                .isInstanceOf(ForbiddenException.class);
        verify(membershipLedger, never()).save(any());
    }

    @Test
    void changeRole_ownerDemotingStillNeedsTheNormalAuthorization() {

        // given - the repair hatch is scoped to promoting to ORGANIZER: role != ORGANIZER short-circuits past even
        // asking whether the caller is an Owner, so a demote gets the ordinary authorization untouched
        when(currentUser.userId()).thenReturn("owner-user");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        doThrow(new ForbiddenException("Organizer role required")).when(membershipAuthorizer)
                .requireOrganizerOrOpenMember("default", 1L, "owner-user", false);

        // when / then
        assertThatThrownBy(() -> sut.changeRole("backend-guild", "bob", CommunityRole.MEMBER))
                .isInstanceOf(ForbiddenException.class);
        verify(membershipLedger, never()).save(any());
        verifyNoInteractions(roleBindings);
    }

    @Test
    void changeRole_demoteLastOrganizerRejected() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("bob").role(CommunityRole.ORGANIZER)
                .build();
        when(membershipLedger.find("default", 1L, "bob")).thenReturn(Optional.of(organizer));
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> sut.changeRole("backend-guild", "bob", CommunityRole.MEMBER))
                .isInstanceOf(ConflictException.class);
        verify(membershipLedger, never()).save(any());
        verify(membershipAuthorizer).requireOrganizer("default", 1L, "dev-user");
    }

    @Test
    void changeRole_demoteWithAnotherOrganizerAllowed() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("bob").role(CommunityRole.ORGANIZER)
                .build();
        when(membershipLedger.find("default", 1L, "bob")).thenReturn(Optional.of(organizer));
        when(membershipLedger.countWithRole("default", 1L, CommunityRole.ORGANIZER)).thenReturn(2L);
        when(membershipLedger.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.changeRole("backend-guild", "bob", CommunityRole.MEMBER);

        // then
        assertThat(actual.role()).isEqualTo(CommunityRole.MEMBER);
        verify(membershipAuthorizer).requireOrganizer("default", 1L, "dev-user");
    }

    @Test
    void changeRole_openMemberCannotGrantOrganizer() {

        // given - the community opened member management, and a plain member targets their own membership
        when(currentUser.userId()).thenReturn("mallory");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).canManageMembers(true).build()));
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("mallory").role(CommunityRole.MEMBER)
                .build();
        when(membershipLedger.find("default", 1L, "mallory")).thenReturn(Optional.of(member));
        doThrow(new ForbiddenException("Organizer role required")).when(membershipAuthorizer)
                .requireOrganizer("default", 1L, "mallory");

        // when / then
        assertThatThrownBy(() -> sut.changeRole("backend-guild", "mallory", CommunityRole.ORGANIZER))
                .isInstanceOf(ForbiddenException.class);
        verify(membershipLedger, never()).save(any());
    }

    @Test
    void changeRole_openMemberCannotDemoteAnOrganizer() {

        // given
        when(currentUser.userId()).thenReturn("mallory");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).canManageMembers(true).build()));
        var organizer = MembershipRecordMother.any().id(9L).communityId(1L).userId("olivia")
                .role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "olivia")).thenReturn(Optional.of(organizer));
        doThrow(new ForbiddenException("Organizer role required")).when(membershipAuthorizer)
                .requireOrganizer("default", 1L, "mallory");

        // when / then
        assertThatThrownBy(() -> sut.changeRole("backend-guild", "olivia", CommunityRole.MEMBER))
                .isInstanceOf(ForbiddenException.class);
        verify(membershipLedger, never()).save(any());
    }

    @Test
    void changeRole_membershipNotFound() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(membershipLedger.find("default", 1L, "bob")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.changeRole("backend-guild", "bob", CommunityRole.ORGANIZER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMembers() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var pageable = PageRequest.of(0, 10);
        var member = MembershipRecordMother.any().id(9L).communityId(1L).userId("alice").build();
        when(membershipLedger.page("default", 1L, pageable)).thenReturn(new PageImpl<>(List.of(member), pageable, 1));

        // when
        var actual = sut.listMembers("backend-guild", pageable);

        // then
        assertThat(actual.getContent()).hasSize(1);
        assertThat(actual.getContent().getFirst().userId()).isEqualTo("alice");
    }

    @Test
    void listingBySortKeyDisplayNamePagesTheNameOrderedRoster() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(memberDirectory.rosterOrderedByName(1L)).thenReturn(List.of("u-ana", "u-bea", "u-carl"));
        when(membershipLedger.findMembers("default", 1L, List.of("u-carl")))
                .thenReturn(List.of(MembershipRecordMother.any().communityId(1L).userId("u-carl").build()));

        // when
        var page = sut.listMembers("backend-guild", PageRequest.of(2, 1, Sort.by("display_name")));

        // then
        assertThat(page.getContent()).extracting(Membership::userId).containsExactly("u-carl");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    /**
     * The rows come back in whatever order the IN clause pleases, so the page has to be put back into the order it was
     * sliced from. Without this, each page is internally shuffled and the listing is only coincidentally alphabetical.
     */
    @Test
    void aNameOrderedPageIsReturnedInThatOrderNotTheRepositorysOwn() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(memberDirectory.rosterOrderedByName(1L)).thenReturn(List.of("u-ana", "u-bea"));
        when(membershipLedger.findMembers("default", 1L, List.of("u-ana", "u-bea")))
                .thenReturn(List.of(MembershipRecordMother.any().communityId(1L).userId("u-bea").build(),
                        MembershipRecordMother.any().communityId(1L).userId("u-ana").build()));

        // when
        var page = sut.listMembers("backend-guild", PageRequest.of(0, 10, Sort.by("display_name")));

        // then
        assertThat(page.getContent()).extracting(Membership::userId).containsExactly("u-ana", "u-bea");
    }

    /**
     * The directory answers ascending and knows nothing of the request, so a descending request is only honoured if
     * this service reverses the roster before slicing it. Serving ascending rows under a {@code desc} sort would be a
     * wrong answer carrying a 200.
     */
    @Test
    void aDescendingNameSortIsServedDescending() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(memberDirectory.rosterOrderedByName(1L)).thenReturn(List.of("u-ana", "u-bea", "u-carl"));
        when(membershipLedger.findMembers("default", 1L, List.of("u-carl", "u-bea")))
                .thenReturn(List.of(MembershipRecordMother.any().communityId(1L).userId("u-bea").build(),
                        MembershipRecordMother.any().communityId(1L).userId("u-carl").build()));

        // when
        var page = sut.listMembers("backend-guild", PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "display_name")));

        // then
        assertThat(page.getContent()).extracting(Membership::userId).containsExactly("u-carl", "u-bea");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void listingByJoinedAtStillUsesThePagedQuery() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        var pageable = PageRequest.of(0, 20, Sort.by("joined_at"));
        when(membershipLedger.page("default", 1L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        sut.listMembers("backend-guild", pageable);

        // then
        verify(memberDirectory, never()).rosterOrderedByName(anyLong());
    }

    @Test
    void listingByDisplayNameWithNoMembersReturnsAnEmptyPage() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(memberDirectory.rosterOrderedByName(1L)).thenReturn(List.of());

        // when
        var page = sut.listMembers("backend-guild", PageRequest.of(0, 10, Sort.by("display_name")));

        // then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(membershipLedger, never()).findMembers(any(), any(), any());
    }

    @Test
    void listingByDisplayNamePastTheLastPageReturnsAnEmptyPageWithTheRealTotal() {

        // given
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(memberDirectory.rosterOrderedByName(1L)).thenReturn(List.of("u-ana"));

        // when
        var page = sut.listMembers("backend-guild", PageRequest.of(1, 10, Sort.by("display_name")));

        // then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(membershipLedger, never()).findMembers(any(), any(), any());
    }

    @Test
    void listMembers_communityNotFound() {

        // given
        when(communityStore.findBySlug("default", "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.listMembers("missing", PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void isMember_trueWhenMembershipExists() {

        // given
        when(membershipLedger.find("default", 1L, "alice"))
                .thenReturn(Optional.of(MembershipRecordMother.any().communityId(1L).userId("alice").build()));

        // when
        var actual = sut.isMember(1L, "alice");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void isMember_falseWhenAbsent() {

        // given
        when(membershipLedger.find("default", 1L, "bob")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.isMember(1L, "bob")).isFalse();
    }

    /**
     * The org-explicit overload, for a caller with no HTTP request to read an {@link OrganizationContext} from - the
     * communication drain's delivery gate, which runs each organization's due work inside
     * {@code OrganizationScope.runAs} and carries no JWT. This test never stubs {@link #organizationContext} beyond the
     * lenient class-wide default, and asserts zero interactions with it: a regression that fell back to the
     * implicit-organization field would still pass the lenient stub but be caught here.
     */
    @Test
    void isMember_orgExplicit_trueWhenMembershipExists() {

        // given
        when(membershipLedger.find("acme", 1L, "alice"))
                .thenReturn(Optional.of(MembershipRecordMother.any().communityId(1L).userId("alice").build()));

        // when
        var actual = sut.isMember("acme", 1L, "alice");

        // then
        assertThat(actual).isTrue();
        verifyNoInteractions(organizationContext);
    }

    @Test
    void isMember_orgExplicit_falseWhenAbsent() {

        // given
        when(membershipLedger.find("acme", 1L, "bob")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.isMember("acme", 1L, "bob")).isFalse();
    }

    @Test
    void isOrganizer_trueOnlyForOrganizerRole() {

        // given
        when(membershipLedger.find("default", 1L, "alice"))
                .thenReturn(Optional.of(MembershipRecordMother.any().role(CommunityRole.ORGANIZER).build()));
        when(membershipLedger.find("default", 1L, "bob"))
                .thenReturn(Optional.of(MembershipRecordMother.any().role(CommunityRole.MEMBER).build()));

        // when / then
        assertThat(sut.isOrganizer(1L, "alice")).isTrue();
        assertThat(sut.isOrganizer(1L, "bob")).isFalse();
    }

    @Test
    void isOrganizer_falseWhenAbsent() {

        // given
        when(membershipLedger.find("default", 1L, "charlie")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.isOrganizer(1L, "charlie")).isFalse();
    }

    /**
     * Org-explicit, same no-JWT caller as {@link #isMember(String, Long, String)} - the communication sweep's
     * producers, which run inside {@code OrganizationScope.runAs} and carry no JWT.
     */
    @Test
    void memberUserIds_orgExplicit_returnsTheRoster() {

        // given
        when(membershipLedger.memberUserIds("acme", 1L)).thenReturn(List.of("user-1", "user-2"));

        // when / then
        assertThat(sut.memberUserIds("acme", 1L)).containsExactly("user-1", "user-2");
        verifyNoInteractions(organizationContext);
    }

    /**
     * Org-explicit, same no-JWT caller. The awaiting-close nudge's other half of its audience, alongside a session's
     * facilitator.
     */
    @Test
    void organizerUserIds_orgExplicit_returnsOnlyTheOrganizers() {

        // given
        when(membershipLedger.userIdsWithRole("acme", 1L, CommunityRole.ORGANIZER)).thenReturn(List.of("lead-1"));

        // when / then
        assertThat(sut.organizerUserIds("acme", 1L)).containsExactly("lead-1");
        verifyNoInteractions(organizationContext);
    }

    @Test
    void myMembership() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        var community = CommunityRecordMother.any().id(7L).build();
        var membership = MembershipRecordMother.any().communityId(7L).userId("alice").role(CommunityRole.ORGANIZER)
                .build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(community));
        when(membershipLedger.find("default", 7L, "alice")).thenReturn(Optional.of(membership));

        // when
        var actual = sut.myMembership("backend-guild");

        // then
        assertThat(actual.role()).isEqualTo(CommunityRole.ORGANIZER);
        assertThat(actual.userId()).isEqualTo("alice");
    }

    @Test
    void myMembership_notAMember() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        var community = CommunityRecordMother.any().id(7L).build();
        when(communityStore.findBySlug("default", "backend-guild")).thenReturn(Optional.of(community));
        when(membershipLedger.find("default", 7L, "alice")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.myMembership("backend-guild")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchMembers_returnsWhatTheDirectoryFindsForAMember() {

        // given
        when(currentUser.userId()).thenReturn("alice");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(membershipLedger.find("default", 1L, "alice"))
                .thenReturn(Optional.of(MembershipRecordMother.any().build()));
        when(memberDirectory.search(1L, "bo")).thenReturn(List.of(new CommunityMember("bob", "Bob Martin")));

        // when
        var actual = sut.searchMembers("backend-guild", "bo");

        // then
        assertThat(actual).containsExactly(new CommunityMember("bob", "Bob Martin"));
    }

    /**
     * Covers both a non-member and a member of a different community: neither has a membership row for THIS community's
     * id, so both are refused before the directory is ever asked.
     */
    @Test
    void searchMembers_forbidsSomebodyOutsideTheCommunity() {

        // given
        when(currentUser.userId()).thenReturn("mallory");
        when(communityStore.findBySlug("default", "backend-guild"))
                .thenReturn(Optional.of(CommunityRecordMother.any().id(1L).build()));
        when(membershipLedger.find("default", 1L, "mallory")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.searchMembers("backend-guild", "bo")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(memberDirectory);
    }
}
