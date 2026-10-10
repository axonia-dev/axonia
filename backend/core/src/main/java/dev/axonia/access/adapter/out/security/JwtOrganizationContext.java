package dev.axonia.access.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.access.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("accessJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
