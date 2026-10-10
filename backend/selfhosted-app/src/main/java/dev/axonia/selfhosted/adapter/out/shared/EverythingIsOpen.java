package dev.axonia.selfhosted.adapter.out.shared;

import org.springframework.stereotype.Component;

import dev.axonia.selfhosted.configuration.SelfHostedProperties;
import dev.axonia.shared.api.paid.PaidAction;
import dev.axonia.shared.api.paid.PaidCapability;
import dev.axonia.shared.api.security.CurrentToken;

/** A company hosting axonia owns all of it: every member of its one Organization may use every capability. */
@Component
class EverythingIsOpen implements PaidCapability {

    private final String organizationId;

    EverythingIsOpen(SelfHostedProperties properties) {
        this.organizationId = properties.organization().id();
    }

    @Override
    public boolean isOpenTo(String organizationId, String subject) {
        return this.organizationId.equals(organizationId);
    }

    @Override
    public boolean isOpenToCaller() {
        return CurrentToken.hasOrganization() && isOpenTo(CurrentToken.organization(), CurrentToken.subject());
    }

    @Override
    public String refusal(PaidAction action) {
        return switch (action) {
            case PROPOSE_REACTION -> "Proposing a reaction is not open to this account.";
            case CONNECT_AGENT -> "Connecting an agent is not open to this account.";
        };
    }
}
