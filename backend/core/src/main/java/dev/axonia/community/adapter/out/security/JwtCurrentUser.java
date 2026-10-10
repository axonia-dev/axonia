package dev.axonia.community.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.community.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
