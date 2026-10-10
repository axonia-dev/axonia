package dev.axonia.bookmarks.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.OrganizationContext;
import dev.axonia.shared.api.security.CurrentToken;

@Component("bookmarksJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {
    @Override
    public String organizationId() {
        return CurrentToken.organization();
    }
}
