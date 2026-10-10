package dev.axonia.expertise.adapter.in.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.expertise.application.ExpertiseDeclarationService;
import lombok.RequiredArgsConstructor;

/** Deletes a departed member's declarations in the community they left, inside the leave transaction. */
@Component
@RequiredArgsConstructor
public class DeclarationMembershipListener {

    private final ExpertiseDeclarationService declarations;

    @EventListener
    public void on(CommunityMemberLeft event) {

        declarations.forgetDepartedDeclarer(event.organizationId(), event.communityId(), event.userId());
    }
}
