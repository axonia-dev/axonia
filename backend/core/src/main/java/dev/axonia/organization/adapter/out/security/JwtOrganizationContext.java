package dev.axonia.organization.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.organization.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("organizationJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
