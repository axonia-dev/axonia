package dev.axonia.community.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("communityJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
