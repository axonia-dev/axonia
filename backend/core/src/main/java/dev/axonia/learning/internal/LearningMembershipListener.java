package dev.axonia.learning.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.community.CommunityMemberLeft;
import dev.axonia.learning.Vocabulary;
import lombok.RequiredArgsConstructor;

/**
 * Deletes a departed member's intents in the community they left, inside the leave transaction, under the locks an add
 * takes. The organization is the event's: a departure can arrive on a path that carries no member token.
 */
@Component
@RequiredArgsConstructor
class LearningMembershipListener {

    private final Vocabulary vocabulary;
    private final LearningIntentRepository repository;

    @EventListener
    void on(CommunityMemberLeft event) {

        vocabulary.shareLock(event.organizationId(), event.communityId());
        repository.lockLearner(event.organizationId(), event.communityId(), event.userId());
        repository.deleteAllIn(event.organizationId(), event.communityId(), event.userId());
    }
}
