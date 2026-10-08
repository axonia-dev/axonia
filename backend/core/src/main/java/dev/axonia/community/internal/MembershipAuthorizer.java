package dev.axonia.community.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ForbiddenException;
import dev.axonia.community.CommunityRole;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MembershipAuthorizer {

    private final MembershipRepository membershipRepository;

    public void requireOrganizer(String organizationId, Long communityId, String userId) {

        var membership = membershipRepository
                .findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .orElseThrow(() -> new ForbiddenException("Organizer role required"));

        if (membership.getRole() != CommunityRole.ORGANIZER) {
            throw new ForbiddenException("Organizer role required");
        }
    }

    public void requireOrganizerOrOpenMember(String organizationId, Long communityId, String userId, boolean open) {

        var membership = membershipRepository
                .findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .orElseThrow(() -> new ForbiddenException("Membership required"));

        if (membership.getRole() == CommunityRole.ORGANIZER) {
            return;
        }
        if (open && membership.getRole() == CommunityRole.MEMBER) {
            return;
        }
        throw new ForbiddenException("Organizer role required");
    }

    @Transactional(readOnly = true)
    public boolean isOrganizerOrOpenMember(String organizationId, Long communityId, String userId, boolean open) {

        return membershipRepository.findByOrganizationIdAndCommunityIdAndUserId(organizationId, communityId, userId)
                .map(m -> m.getRole() == CommunityRole.ORGANIZER || (open && m.getRole() == CommunityRole.MEMBER))
                .orElse(false);
    }
}
