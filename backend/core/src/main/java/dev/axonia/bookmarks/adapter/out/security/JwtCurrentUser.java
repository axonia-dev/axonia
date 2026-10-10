package dev.axonia.bookmarks.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("bookmarksJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {
    @Override
    public String userId() {
        return CurrentToken.subject();
    }
}
