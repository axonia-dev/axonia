package dev.axonia.bookmarks.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.bookmarks.CurrentUser;

@Component("bookmarksJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {
    @Override
    public String userId() {
        return CurrentToken.subject();
    }
}
