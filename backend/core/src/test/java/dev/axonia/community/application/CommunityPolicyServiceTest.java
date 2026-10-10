package dev.axonia.community.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunicationSettings;
import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.community.api.TopicManagementPolicy;
import dev.axonia.community.application.port.out.CommunityStore;
import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityRecordMother;
import dev.axonia.community.domain.ManagementPolicy;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityPolicyServiceTest {

    @Mock
    private CommunityStore communityStore;

    @Mock
    private CommunityLookup communities;

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @InjectMocks
    private CommunityPolicyService sut;

    @BeforeEach
    void seedSeams() {

        lenient().when(organizationContext.organizationId()).thenReturn("default");
    }

    @Test
    void getContributionPolicy() {

        // given
        var entity = CommunityRecordMother.any().id(1L).canQueueTopics(true).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getContributionPolicy("backend-guild");

        // then
        assertThat(actual).isEqualTo(new ContributionPolicy(false, false, true, false));
    }

    @Test
    void getContributionPolicy_defaultsToCurated() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getContributionPolicy("backend-guild");

        // then
        assertThat(actual).isEqualTo(ContributionPolicy.curated());
    }

    @Test
    void getContributionPolicy_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.getContributionPolicy("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getContributionPolicyById() {

        // given
        var entity = CommunityRecordMother.any().id(1L).canStartSeries(true).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);

        // when
        var actual = sut.getContributionPolicyById(1L);

        // then
        assertThat(actual).isEqualTo(new ContributionPolicy(true, false, false, false));
    }

    @Test
    void getContributionPolicyById_notFound() {

        // given
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.getContributionPolicyById(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTopicManagementPolicy() {

        // given
        var entity = CommunityRecordMother.any().id(1L).topicManagementPolicy(TopicManagementPolicy.ANY_MEMBER).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);

        // when
        var actual = sut.getTopicManagementPolicy(1L);

        // then
        assertThat(actual).isEqualTo(TopicManagementPolicy.ANY_MEMBER);
    }

    @Test
    void getTopicManagementPolicy_notFound() {

        // given
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.getTopicManagementPolicy(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateContributionPolicy() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.updateContributionPolicy("backend-guild",
                new UpdateContributionPolicyRequest(true, true, false, false));

        // then
        assertThat(actual).isEqualTo(new ContributionPolicy(true, true, false, false));
        verify(membershipAuthorizer).requireOrganizerOrOpenMember("default", 1L, "dev-user", false);
    }

    @Test
    void updateContributionPolicy_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.updateContributionPolicy("missing",
                new UpdateContributionPolicyRequest(true, true, true, false))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateContributionPolicy_persistsCanManageSchedule() {

        var entity = CommunityRecordMother.any().id(1L).build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(communities.findBySlugOrThrow("t1", "guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = new UpdateContributionPolicyRequest(false, false, false, true);
        var policy = sut.updateContributionPolicy("guild", request);
        assertThat(policy.canManageSchedule()).isTrue();
    }

    @Test
    void getManagementPolicy() {

        // given
        var entity = CommunityRecordMother.any().id(1L).canManageMembers(true).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getManagementPolicy("backend-guild");

        // then
        assertThat(actual)
                .isEqualTo(new ManagementPolicy(false, false, true, false, false, false, false, false, false, false));
    }

    @Test
    void getManagementPolicy_defaultsToCurated() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getManagementPolicy("backend-guild");

        // then
        assertThat(actual).isEqualTo(ManagementPolicy.curated());
    }

    @Test
    void updateManagementPolicy() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.updateManagementPolicy("backend-guild",
                new UpdateManagementPolicyRequest(true, false, true, false, true, false, false, false, false, false));

        // then
        assertThat(actual)
                .isEqualTo(new ManagementPolicy(true, false, true, false, true, false, false, false, false, false));
        verify(membershipAuthorizer).requireOrganizer("default", 1L, "dev-user");
    }

    @Test
    void updateManagementPolicy_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.updateManagementPolicy("missing",
                new UpdateManagementPolicyRequest(true, true, true, true, true, false, false, false, false, false)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateManagementPolicy_roundTripsCurationAndHealthAxes() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var updated = sut.updateManagementPolicy("backend-guild",
                new UpdateManagementPolicyRequest(false, false, false, false, false, true, true, false, false, false));

        // then
        assertThat(updated.canCurateTags()).isTrue();
        assertThat(updated.canViewHealth()).isTrue();
    }

    @Test
    void updateManagementPolicy_roundTripsInviteAxis() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var updated = sut.updateManagementPolicy("backend-guild",
                new UpdateManagementPolicyRequest(false, false, false, false, false, false, false, true, false, false));

        // then
        assertThat(updated.canInviteMembers()).isTrue();
    }

    @Test
    void updateManagementPolicy_roundTripsCreditContributionsAxis() {

        // given
        when(currentUser.userId()).thenReturn("dev-user");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var updated = sut.updateManagementPolicy("backend-guild",
                new UpdateManagementPolicyRequest(false, false, false, false, false, false, false, false, false, true));

        // then
        assertThat(updated.canCreditContributions()).isTrue();
    }

    @Test
    void curatedManagementPolicyKeepsCreditContributionsOrganizerOnly() {

        assertThat(ManagementPolicy.curated().canCreditContributions()).isFalse();
    }

    @Test
    void managementPolicyCarriesTheCommunicationAxis() {

        assertThat(ManagementPolicy.curated().canManageCommunication()).isFalse();
    }

    @Test
    void getCommunicationSettings() {

        // given
        var entity = CommunityRecordMother.any().id(1L).notifySessionUpcoming(false).notifySessionWrapUp(true)
                .sessionUpcomingLeadDays(5).wrapUpGraceHours(72).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getCommunicationSettings("backend-guild");

        // then
        assertThat(actual).isEqualTo(new CommunicationSettings(false, true, 5, 72));
    }

    @Test
    void getCommunicationSettings_defaultsToOnByDefault() {

        // given
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("default", "backend-guild")).thenReturn(entity);

        // when
        var actual = sut.getCommunicationSettings("backend-guild");

        // then
        assertThat(actual).isEqualTo(new CommunicationSettings(true, true, 3, 48));
    }

    @Test
    void getCommunicationSettings_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.getCommunicationSettings("missing")).isInstanceOf(NotFoundException.class);
    }

    /**
     * Org-explicit rather than reading {@link OrganizationContext}: its sole caller,
     * {@code events.adapter.out.community.CommunityAccessAdapter}, is reached both from a normal request and from the
     * communication drain's delivery gate, which runs each organization's due work inside
     * {@code OrganizationScope.runAs} and carries no JWT. This test never stubs {@link #organizationContext} beyond the
     * lenient class-wide default and asserts zero interactions with it: a regression that fell back to reading it would
     * still pass the lenient stub but be caught here.
     */
    @Test
    void getCommunicationSettingsById() {

        // given
        var entity = CommunityRecordMother.any().id(1L).notifySessionUpcoming(false).notifySessionWrapUp(true)
                .sessionUpcomingLeadDays(5).wrapUpGraceHours(72).build();
        when(communities.findByIdOrThrow("acme", 1L)).thenReturn(entity);

        // when
        var actual = sut.getCommunicationSettingsById("acme", 1L);

        // then
        assertThat(actual).isEqualTo(new CommunicationSettings(false, true, 5, 72));
        verifyNoInteractions(organizationContext);
    }

    @Test
    void getCommunicationSettingsById_notFound() {

        // given
        when(communities.findByIdOrThrow("acme", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.getCommunicationSettingsById("acme", 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateCommunicationSettingsRequiresOrganizerUnlessDelegated() {

        // given
        when(organizationContext.organizationId()).thenReturn("org-1");
        when(currentUser.userId()).thenReturn("user-1");
        var entity = CommunityRecordMother.any().id(1L).canManageCommunication(true).build();
        when(communities.findBySlugOrThrow("org-1", "frontend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        sut.updateCommunicationSettings("frontend-guild", new UpdateCommunicationSettingsRequest(true, false, 5, 72));

        // then
        verify(membershipAuthorizer).requireOrganizerOrOpenMember("org-1", entity.getId(), "user-1", true);
    }

    @Test
    void updateCommunicationSettingsStoresEveryAxis() {

        // given
        when(organizationContext.organizationId()).thenReturn("org-1");
        when(currentUser.userId()).thenReturn("user-1");
        var entity = CommunityRecordMother.any().id(1L).build();
        when(communities.findBySlugOrThrow("org-1", "frontend-guild")).thenReturn(entity);
        when(communityStore.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        var saved = sut.updateCommunicationSettings("frontend-guild",
                new UpdateCommunicationSettingsRequest(true, false, 5, 72));

        // then
        assertThat(saved).isEqualTo(new CommunicationSettings(true, false, 5, 72));
        verify(membershipAuthorizer).requireOrganizerOrOpenMember("org-1", entity.getId(), "user-1", false);
    }

    @Test
    void updateCommunicationSettings_notFound() {

        // given
        when(communities.findBySlugOrThrow("default", "missing"))
                .thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.updateCommunicationSettings("missing",
                new UpdateCommunicationSettingsRequest(true, true, 3, 48))).isInstanceOf(NotFoundException.class);
    }
}
