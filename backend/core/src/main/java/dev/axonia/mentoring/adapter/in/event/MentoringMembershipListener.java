package dev.axonia.mentoring.adapter.in.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.mentoring.application.PairingService;
import lombok.RequiredArgsConstructor;

/** A member who leaves a guild ends their pairings there and leaves its mentoring page, in the leave transaction. */
@Component
@RequiredArgsConstructor
class MentoringMembershipListener {

    private final PairingService pairingService;

    @EventListener
    void on(CommunityMemberLeft event) {

        pairingService.departedFromGuild(event.organizationId(), event.communityId(), event.userId());
    }
}
