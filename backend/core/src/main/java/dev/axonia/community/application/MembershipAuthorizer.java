package dev.axonia.community.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MembershipAuthorizer {

    private final MembershipLedger membershipLedger;

    public void requireOrganizer(String organizationId, Long communityId, String userId) {

        var membership = membershipLedger.find(organizationId, communityId, userId)
                .orElseThrow(() -> new ForbiddenException("Organizer role required"));

        if (membership.getRole() != CommunityRole.ORGANIZER) {
            throw new ForbiddenException("Organizer role required");
        }
    }

    public void requireOrganizerOrOpenMember(String organizationId, Long communityId, String userId, boolean open) {

        var membership = membershipLedger.find(organizationId, communityId, userId)
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

        return membershipLedger.find(organizationId, communityId, userId)
                .map(m -> m.getRole() == CommunityRole.ORGANIZER || (open && m.getRole() == CommunityRole.MEMBER))
                .orElse(false);
    }
}
