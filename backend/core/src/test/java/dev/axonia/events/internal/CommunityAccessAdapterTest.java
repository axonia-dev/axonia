package dev.axonia.events.internal;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort.ChatFailure;
import dev.axonia.communication.ChatFailureKind;
import dev.axonia.community.ChatDestinationService;
import dev.axonia.community.CommunicationSettings;
import dev.axonia.community.Community;
import dev.axonia.community.CommunityCapabilities;
import dev.axonia.community.CommunityIdentity;
import dev.axonia.community.CommunityPolicyService;
import dev.axonia.community.CommunityService;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.MemberMatch;
import dev.axonia.community.MembershipService;
import dev.axonia.community.TopicManagementPolicy;
import dev.axonia.events.OrganizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAccessAdapterTest {

    @Mock
    private CommunityService communityService;

    @Mock
    private CommunityPolicyService communityPolicyService;

    @Mock
    private CommunityCapabilities communityCapabilities;

    @Mock
    private MembershipService membershipService;

    @Mock
    private MemberDirectory memberDirectory;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ChatDestinationService chatDestinationService;

    @InjectMocks
    private CommunityAccessAdapter sut;

    @Test
    void communityIdForSlug_returnsTheCommunityId() {

        // given
        when(communityService.getBySlug("backend-guild")).thenReturn(Community.builder().id(7L).build());

        // when
        var actual = sut.communityIdForSlug("backend-guild");

        // then
        assertThat(actual).isEqualTo(7L);
    }

    /**
     * The only overload this port has: every caller is a producer running inside the communication sweep's
     * {@code OrganizationScope.runAs}, which carries no JWT - so this must reach {@code CommunityService}'s
     * org-explicit {@code identityById}, never an implicit-organization lookup that would throw outside a request.
     */
    @Test
    void communityIdentity_delegatesToCommunityServicesIdentityById() {

        // given
        var identity = new CommunityIdentity("backend-guild", "Backend Guild", "#1aa6a0");
        when(communityService.identityById("acme", 7L)).thenReturn(identity);

        // when / then
        assertThat(sut.communityIdentity("acme", 7L)).isEqualTo(identity);
    }

    @Test
    void isMember_delegatesToMembershipService() {

        // given
        when(membershipService.isMember(7L, "alice")).thenReturn(true);

        // when / then
        assertThat(sut.isMember(7L, "alice")).isTrue();
    }

    @Test
    void isOrganizer_delegatesToMembershipService() {

        // given
        when(membershipService.isOrganizer(7L, "alice")).thenReturn(true);

        // when / then
        assertThat(sut.isOrganizer(7L, "alice")).isTrue();
    }

    @Test
    void contributionPolicy_delegatesToCommunityService() {

        // given
        var policy = new ContributionPolicy(true, false, false, false);
        when(communityPolicyService.getContributionPolicyById(7L)).thenReturn(policy);

        // when
        var actual = sut.contributionPolicy(7L);

        // then
        assertThat(actual).isEqualTo(policy);
    }

    /**
     * A session's own reminder and wrap-up are gated on this, so events asks the community module whether this person
     * holds the axis rather than evaluating a raw policy record itself.
     */
    @Test
    void canManageCommunication_delegatesToCommunityCapabilities() {

        // given
        when(communityCapabilities.canManageCommunication(7L, "dev-user")).thenReturn(true);

        // when / then
        assertThat(sut.canManageCommunication(7L, "dev-user")).isTrue();
    }

    @Test
    void topicManagementPolicy_delegatesToCommunityService() {

        // given
        when(communityPolicyService.getTopicManagementPolicy(7L)).thenReturn(TopicManagementPolicy.ANY_MEMBER);

        // when / then
        assertThat(sut.topicManagementPolicy(7L)).isEqualTo(TopicManagementPolicy.ANY_MEMBER);
    }

    /**
     * The implicit-organization overload resolves the organization from {@code events}' own request-scoped
     * {@code OrganizationContext} rather than reading {@code CommunityPolicyService}'s (which this adapter no longer
     * has access to) - both ultimately read the same JWT claim for a real request.
     */
    @Test
    void communicationSettings_delegatesToCommunityPolicyServiceWithTheRequestsOrganization() {

        // given
        var settings = new CommunicationSettings(true, true, 3, 48);
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityPolicyService.getCommunicationSettingsById("default", 7L)).thenReturn(settings);

        // when / then
        assertThat(sut.communicationSettings(7L)).isEqualTo(settings);
    }

    /**
     * The org-explicit overload, for a caller with no JWT to read an organization from - the communication drain's
     * delivery gate. Must reach {@code CommunityPolicyService}'s org-explicit overload, never the implicit-organization
     * one that would throw outside a request.
     */
    @Test
    void communicationSettings_orgExplicit_delegatesToCommunityPolicyServicesOrgExplicitOverload() {

        // given
        var settings = new CommunicationSettings(true, true, 3, 48);
        when(communityPolicyService.getCommunicationSettingsById("acme", 7L)).thenReturn(settings);

        // when / then
        assertThat(sut.communicationSettings("acme", 7L)).isEqualTo(settings);
    }

    /**
     * The org-explicit overload of {@code isMember}, for the same no-JWT caller.
     */
    @Test
    void isMember_orgExplicit_delegatesToMembershipServicesOrgExplicitOverload() {

        // given
        when(membershipService.isMember("acme", 7L, "alice")).thenReturn(true);

        // when / then
        assertThat(sut.isMember("acme", 7L, "alice")).isTrue();
    }

    /**
     * The port carries no matching logic of its own: resolution belongs to {@code community}, and a second copy of it
     * would be free to drift into crediting the wrong person. What is pinned here is that the verdict, ambiguity count
     * and all, crosses the boundary intact.
     */
    @Test
    void resolveMember_delegatesToTheMemberDirectory() {

        // given
        when(memberDirectory.resolve(7L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));

        // when / then
        assertThat(sut.resolveMember(7L, "Alice Dupont")).isEqualTo(new MemberMatch.Matched("alice-id"));
    }

    @Test
    void resolveMember_reportsHowManyMembersAnAmbiguousReferenceMatched() {

        // given - the count is what lets a caller tell "two people share this name" from "your query was too vague"
        when(memberDirectory.resolve(7L, "Bob")).thenReturn(new MemberMatch.Ambiguous(3));

        // when / then
        assertThat(sut.resolveMember(7L, "Bob")).isEqualTo(new MemberMatch.Ambiguous(3));
    }

    @Test
    void memberUserIds_delegatesToMembershipServicesOrgExplicitOverload() {

        // given
        when(membershipService.memberUserIds("acme", 7L)).thenReturn(List.of("user-1", "user-2"));

        // when / then
        assertThat(sut.memberUserIds("acme", 7L)).containsExactly("user-1", "user-2");
    }

    @Test
    void organizerUserIds_delegatesToMembershipServicesOrgExplicitOverload() {

        // given
        when(membershipService.organizerUserIds("acme", 7L)).thenReturn(List.of("lead-1"));

        // when / then
        assertThat(sut.organizerUserIds("acme", 7L)).containsExactly("lead-1");
    }

    @Test
    void chatDestinationStatus_delegatesToChatDestinationServicesStatusFor() {

        // given
        var ready = new ChatChannelStatus.Ready(URI.create("https://hooks.slack.com/services/x"));
        when(chatDestinationService.statusFor("acme", 7L)).thenReturn(ready);

        // when / then
        assertThat(sut.chatDestinationStatus("acme", 7L)).isEqualTo(ready);
    }

    @Test
    void chatDestinationIds_delegatesToChatDestinationServicesDestinationIdsFor() {

        // given
        when(chatDestinationService.destinationIdsFor("acme", 7L)).thenReturn(List.of(11L, 12L));

        // when / then
        assertThat(sut.chatDestinationIds("acme", 7L)).containsExactly(11L, 12L);
    }

    @Test
    void recordChatDelivered_delegatesToChatDestinationService() {

        // given
        var at = Instant.parse("2026-08-07T12:00:00Z");

        // when
        sut.recordChatDelivered("acme", 7L, at);

        // then
        verify(chatDestinationService).recordChatDelivered("acme", 7L, at);
    }

    @Test
    void recordChatFailure_delegatesToChatDestinationServiceWithTheCallersBrokenAfterThreshold() {

        // given
        var failure = new ChatFailure(ChatFailureKind.GONE, "410 Gone", Instant.parse("2026-08-07T12:00:00Z"));

        // when
        sut.recordChatFailure("acme", 7L, failure, 3);

        // then
        verify(chatDestinationService).recordChatFailure("acme", 7L, failure, 3);
    }
}
