package dev.axonia.recognition.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("recognitionJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
