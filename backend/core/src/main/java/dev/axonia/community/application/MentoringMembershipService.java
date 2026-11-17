package dev.axonia.community.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.MentoringMemberships;
import dev.axonia.community.application.port.out.MembershipLedger;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MentoringMembershipService implements MentoringMemberships {

    private final MembershipLedger membershipLedger;

    @Override
    @Transactional(readOnly = true)
    public boolean inAMentoringCommunity(String organizationId, String userId) {

        return membershipLedger.inAMentoringCommunity(organizationId, userId);
    }
}
