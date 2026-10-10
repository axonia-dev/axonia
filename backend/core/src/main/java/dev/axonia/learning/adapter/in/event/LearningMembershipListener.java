package dev.axonia.learning.adapter.in.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.learning.application.LearningService;
import lombok.RequiredArgsConstructor;

/**
 * Deletes a departed member's intents in the community they left, inside the leave transaction, under the locks an add
 * takes. The organization is the event's: a departure can arrive on a path that carries no member token.
 */
@Component
@RequiredArgsConstructor
class LearningMembershipListener {

    private final LearningService learning;

    @EventListener
    void on(CommunityMemberLeft event) {

        learning.forgetDepartedLearner(event.organizationId(), event.communityId(), event.userId());
    }
}
