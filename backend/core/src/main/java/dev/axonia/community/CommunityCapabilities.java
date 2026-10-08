package dev.axonia.community;

import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.internal.Communities;
import dev.axonia.community.internal.CommunityEntity;
import dev.axonia.community.internal.MembershipAuthorizer;
import lombok.RequiredArgsConstructor;

/**
 * Reads of the management-policy axes: who may do what inside a community. An organizer always holds a capability; a
 * member holds it only where the community has opened that axis. Separate from {@link CommunityService}, whose concern
 * is the community's lifecycle and its CRUD, and from {@link CommunityPolicyService}, which hands back the raw policy
 * record instead of evaluating a single axis for one caller into a boolean.
 */
@Service
@RequiredArgsConstructor
public class CommunityCapabilities {

    private final Communities communities;
    private final MembershipAuthorizer membershipAuthorizer;
    private final OrganizationContext organizationContext;

    @Transactional(readOnly = true)
    public boolean canCurateTags(long communityId, String userId) {

        return holds(communityId, userId, CommunityEntity::isCanCurateTags);
    }

    @Transactional(readOnly = true)
    public boolean canViewHealth(long communityId, String userId) {

        return holds(communityId, userId, CommunityEntity::isCanViewHealth);
    }

    @Transactional(readOnly = true)
    public boolean canInviteMembers(long communityId, String userId) {

        return holds(communityId, userId, CommunityEntity::isCanInviteMembers);
    }

    /**
     * Who may govern this community's outgoing mail: the communication settings themselves, and an individual session's
     * reminder and wrap-up - whether to preview one, move it, or suppress it. One axis for both, because they are the
     * same power at two scales: a person trusted to decide whether wrap-ups go out at all is trusted to reschedule one.
     */
    @Transactional(readOnly = true)
    public boolean canManageCommunication(long communityId, String userId) {

        return holds(communityId, userId, CommunityEntity::isCanManageCommunication);
    }

    /**
     * Who may say whose work an artifact is, when it is not their own: naming another member as the contributor at
     * capture, and correcting the contributor afterwards. One axis for both, because a person trusted to credit a
     * colleague on the way in is trusted to fix a credit that went to the wrong person.
     */
    @Transactional(readOnly = true)
    public boolean canCreditContributions(long communityId, String userId) {

        return holds(communityId, userId, CommunityEntity::isCanCreditContributions);
    }

    private boolean holds(long communityId, String userId, Predicate<CommunityEntity> axis) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findByIdOrThrow(organizationId, communityId);
        return membershipAuthorizer.isOrganizerOrOpenMember(organizationId, entity.getId(), userId, axis.test(entity));
    }
}
