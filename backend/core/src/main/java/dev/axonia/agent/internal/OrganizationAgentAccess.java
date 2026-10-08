package dev.axonia.agent.internal;

import org.springframework.stereotype.Component;

import dev.axonia.PaidAction;
import dev.axonia.PaidCapability;
import dev.axonia.agent.AgentAccess;
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
