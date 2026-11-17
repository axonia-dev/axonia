package dev.axonia.mentoring.adapter.in.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.axonia.identity.api.MemberDeactivated;
import dev.axonia.mentoring.application.PairingService;
import lombok.RequiredArgsConstructor;

/** A deactivated member's pairings end in every guild of the organization. */
@Component
@RequiredArgsConstructor
class MentoringDeactivationListener {

    private final PairingService pairingService;

    @EventListener
    void on(MemberDeactivated event) {

        pairingService.deactivated(event.organizationId(), event.userId());
    }
}
