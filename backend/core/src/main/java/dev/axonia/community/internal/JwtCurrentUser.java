package dev.axonia.community.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.community.CurrentUser;

@Component
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
