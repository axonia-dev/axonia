package dev.axonia.expertise.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("expertiseJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
