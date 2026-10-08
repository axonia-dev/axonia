package dev.axonia.knowledge.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.knowledge.OrganizationContext;

@Component("knowledgeJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {

    @Override
    public String organizationId() {

        return CurrentToken.organization();
    }
}
