package dev.axonia.community;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.internal.Communities;
import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.CommunityRepository;
import dev.axonia.community.internal.MembershipAuthorizer;
import lombok.RequiredArgsConstructor;

/**
 * Reads and writes of a community's policy axes: {@link ContributionPolicy}, {@link ManagementPolicy},
 * {@link TopicManagementPolicy} and {@link CommunicationSettings}. Separate from {@link CommunityService}, whose
 * concern is the community's lifecycle and its CRUD - none of these settings change what a community IS, only what it
 * permits or how it communicates.
 *
 * <p>
 * Also separate from {@link CommunityCapabilities}, even though both read the same policy columns off
 * {@link CommunityEntity}: this class hands back the raw policy record itself (what the settings page reads and writes,
 * one whole {@link ManagementPolicy} etc. at a time), while {@link CommunityCapabilities} evaluates a single axis for
 * one caller (organizer-or-open-axis) into a plain boolean. Neither is a special case of the other, so neither depends
 * on the other.
 */
@Service
@RequiredArgsConstructor
public class CommunityPolicyService {

    private final CommunityRepository communityRepository;
    private final Communities communities;
    private final MembershipAuthorizer membershipAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;

    @Transactional(readOnly = true)
    public ContributionPolicy getContributionPolicy(String slug) {

        var organizationId = organizationContext.organizationId();
        return toPolicy(communities.findBySlugOrThrow(organizationId, slug));
    }

    @Transactional(readOnly = true)
    public ContributionPolicy getContributionPolicyById(long communityId) {

        var entity = communities.findByIdOrThrow(organizationContext.organizationId(), communityId);
        return toPolicy(entity);
    }

    @Transactional(readOnly = true)
    public TopicManagementPolicy getTopicManagementPolicy(long communityId) {

        var organizationId = organizationContext.organizationId();
        return communities.findByIdOrThrow(organizationId, communityId).getTopicManagementPolicy();
    }

    /**
     * The communication settings of a community reached by id rather than slug - for {@code events}, which knows a
     * session's {@code communityId} but never its slug, to read the wrap-up grace period when closing a session.
     * Org-explicit rather than reading {@link #organizationContext}: {@code events.internal.CommunityAccessAdapter} is
     * the only caller, for both a caller inside a request (which already has its own {@code events.OrganizationContext}
     * to resolve from) and the communication drain's delivery gate, which runs inside {@code OrganizationScope.runAs}
     * and carries no JWT - reading {@link #organizationContext} here would throw for the latter rather than resolve.
     */
    @Transactional(readOnly = true)
    public CommunicationSettings getCommunicationSettingsById(String organizationId, long communityId) {

        return toCommunicationSettings(communities.findByIdOrThrow(organizationId, communityId));
    }

    @Transactional
    public ContributionPolicy updateContributionPolicy(String slug, UpdateContributionPolicyRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findBySlugOrThrow(organizationId, slug);
        membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, entity.getId(), currentUser.userId(),
                entity.isCanManageContributionPolicy());

        var saved = communityRepository.save(entity.toBuilder().canStartSeries(request.canStartSeries())
                .canEditStructure(request.canEditStructure()).canQueueTopics(request.canQueueTopics())
                .canManageSchedule(request.canManageSchedule()).build());
        return toPolicy(saved);
    }

    @Transactional(readOnly = true)
    public ManagementPolicy getManagementPolicy(String slug) {

        var organizationId = organizationContext.organizationId();
        return toManagementPolicy(communities.findBySlugOrThrow(organizationId, slug));
    }

    @Transactional
    public ManagementPolicy updateManagementPolicy(String slug, UpdateManagementPolicyRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findBySlugOrThrow(organizationId, slug);
        membershipAuthorizer.requireOrganizer(organizationId, entity.getId(), currentUser.userId());

        var saved = communityRepository.save(entity.toBuilder().canEditCommunity(request.canEditCommunity())
                .canManageLookAndFeel(request.canManageLookAndFeel()).canManageMembers(request.canManageMembers())
                .canManageContributionPolicy(request.canManageContributionPolicy())
                .canArchiveCommunity(request.canArchiveCommunity()).canCurateTags(request.canCurateTags())
                .canViewHealth(request.canViewHealth()).canInviteMembers(request.canInviteMembers())
                .canManageCommunication(request.canManageCommunication())
                .canCreditContributions(request.canCreditContributions()).build());
        return toManagementPolicy(saved);
    }

    @Transactional(readOnly = true)
    public CommunicationSettings getCommunicationSettings(String slug) {

        return toCommunicationSettings(communities.findBySlugOrThrow(organizationContext.organizationId(), slug));
    }

    @Transactional
    public CommunicationSettings updateCommunicationSettings(String slug, UpdateCommunicationSettingsRequest request) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findBySlugOrThrow(organizationId, slug);
        membershipAuthorizer.requireOrganizerOrOpenMember(organizationId, entity.getId(), currentUser.userId(),
                entity.isCanManageCommunication());

        var saved = communityRepository.save(entity.toBuilder().notifySessionUpcoming(request.notifySessionUpcoming())
                .notifySessionWrapUp(request.notifySessionWrapUp())
                .sessionUpcomingLeadDays(request.sessionUpcomingLeadDays()).wrapUpGraceHours(request.wrapUpGraceHours())
                .build());
        return toCommunicationSettings(saved);
    }

    private ContributionPolicy toPolicy(CommunityEntity e) {

        return new ContributionPolicy(e.isCanStartSeries(), e.isCanEditStructure(), e.isCanQueueTopics(),
                e.isCanManageSchedule());
    }

    private ManagementPolicy toManagementPolicy(CommunityEntity e) {

        return new ManagementPolicy(e.isCanEditCommunity(), e.isCanManageLookAndFeel(), e.isCanManageMembers(),
                e.isCanManageContributionPolicy(), e.isCanArchiveCommunity(), e.isCanCurateTags(), e.isCanViewHealth(),
                e.isCanInviteMembers(), e.isCanManageCommunication(), e.isCanCreditContributions());
    }

    private CommunicationSettings toCommunicationSettings(CommunityEntity e) {

        return new CommunicationSettings(e.isNotifySessionUpcoming(), e.isNotifySessionWrapUp(),
                e.getSessionUpcomingLeadDays(), e.getWrapUpGraceHours());
    }
}
