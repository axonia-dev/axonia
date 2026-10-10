package dev.axonia.health.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.health.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("healthJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
