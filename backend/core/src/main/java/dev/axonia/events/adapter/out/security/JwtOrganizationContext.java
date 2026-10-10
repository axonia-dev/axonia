package dev.axonia.events.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("eventsJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
