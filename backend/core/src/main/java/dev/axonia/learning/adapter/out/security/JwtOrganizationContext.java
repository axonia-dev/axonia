package dev.axonia.learning.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.learning.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("learningJwtOrganizationContext")
public class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
