package dev.axonia.agent.application;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.paid.PaidAction;
import dev.axonia.shared.api.paid.PaidCapability;
import lombok.RequiredArgsConstructor;

/**
 * Agent access is the paid axis, asked about an Organization. The rule itself is the edition's {@link PaidCapability},
 * because the reaction catalog asks the same question and two copies would be two answers.
 */
@Component
@RequiredArgsConstructor
class OrganizationAgentAccess implements AgentAccess {

    private final PaidCapability paidCapability;

    @Override
    public boolean isAvailableTo(String organizationId, String subject) {

        return paidCapability.isOpenTo(organizationId, subject);
    }

    @Override
    public boolean isAvailableToCaller() {

        return paidCapability.isOpenToCaller();
    }

    @Override
    public String refusal() {

        return paidCapability.refusal(PaidAction.CONNECT_AGENT);
    }
}
