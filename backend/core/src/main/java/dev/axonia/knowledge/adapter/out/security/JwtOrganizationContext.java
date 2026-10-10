package dev.axonia.knowledge.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("knowledgeJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
