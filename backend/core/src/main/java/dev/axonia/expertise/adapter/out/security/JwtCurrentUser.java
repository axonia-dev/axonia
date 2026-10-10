package dev.axonia.expertise.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("expertiseJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
