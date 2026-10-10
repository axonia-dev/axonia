package dev.axonia.community.application;

import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.community.domain.CommunityRecord;
import lombok.RequiredArgsConstructor;

/**
 * Reads of the management-policy axes: who may do what inside a community. An organizer always holds a capability; a
 * member holds it only where the community has opened that axis. Separate from {@link CommunityService}, whose concern
 * is the community's lifecycle and its CRUD, and from {@link CommunityPolicyService}, which hands back the raw policy
 * record instead of evaluating a single axis for one caller into a boolean.
 */
@Service
@RequiredArgsConstructor
public class CommunityCapabilityService implements dev.axonia.community.api.CommunityCapabilities {

    private final CommunityLookup communities;
    private final MembershipAuthorizer membershipAuthorizer;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public boolean canCurateTags(long communityId, String userId) {

        return holds(communityId, userId, CommunityRecord::isCanCurateTags);
    }

    @Transactional(readOnly = true)
    public boolean canViewHealth(long communityId, String userId) {

        return holds(communityId, userId, CommunityRecord::isCanViewHealth);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canInviteMembers(long communityId, String userId) {

        return holds(communityId, userId, CommunityRecord::isCanInviteMembers);
    }

    /**
     * Who may govern this community's outgoing mail: the communication settings themselves, and an individual session's
     * reminder and wrap-up - whether to preview one, move it, or suppress it. One axis for both, because they are the
     * same power at two scales: a person trusted to decide whether wrap-ups go out at all is trusted to reschedule one.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean canManageCommunication(long communityId, String userId) {

        return holds(communityId, userId, CommunityRecord::isCanManageCommunication);
    }

    /**
     * Who may say whose work an artifact is, when it is not their own: naming another member as the contributor at
     * capture, and correcting the contributor afterwards. One axis for both, because a person trusted to credit a
     * colleague on the way in is trusted to fix a credit that went to the wrong person.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean canCreditContributions(long communityId, String userId) {

        return holds(communityId, userId, CommunityRecord::isCanCreditContributions);
    }

    private boolean holds(long communityId, String userId, Predicate<CommunityRecord> axis) {

        var organizationId = organizationContext.organizationId();
        var entity = communities.findByIdOrThrow(organizationId, communityId);
        return membershipAuthorizer.isOrganizerOrOpenMember(organizationId, entity.getId(), userId, axis.test(entity));
    }
}
