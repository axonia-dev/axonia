package dev.axonia.roundup.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.roundup.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("roundupJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
