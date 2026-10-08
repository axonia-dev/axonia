package dev.axonia.recognition.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.recognition.OrganizationContext;

@Component("recognitionJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
