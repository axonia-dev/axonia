package dev.axonia.expertise.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.community.CommunityMemberLeft;
import lombok.RequiredArgsConstructor;

/** Deletes a departed member's declarations in the community they left, inside the leave transaction. */
@Component
@RequiredArgsConstructor
class DeclarationMembershipListener {

    private final DeclarationRepository declarationRepository;
    private final VocabularyLock vocabularyLock;

    @EventListener
    void on(CommunityMemberLeft event) {

        vocabularyLock.share(event.organizationId(), event.communityId());
        declarationRepository.lockDeclarer(event.organizationId(), event.communityId(), event.userId());
        declarationRepository.deleteAllIn(event.organizationId(), event.communityId(), event.userId());
    }
}
