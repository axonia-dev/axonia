package dev.axonia.health.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.health.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("healthJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
