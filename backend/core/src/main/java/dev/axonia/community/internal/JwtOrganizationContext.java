package dev.axonia.community.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.community.OrganizationContext;

@Component("communityJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
