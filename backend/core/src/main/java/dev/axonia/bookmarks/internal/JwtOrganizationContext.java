package dev.axonia.bookmarks.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.bookmarks.OrganizationContext;

@Component("bookmarksJwtOrganizationContext")
class JwtOrganizationContext implements OrganizationContext {
    @Override
    public String organizationId() {
        return CurrentToken.organization();
    }
}
