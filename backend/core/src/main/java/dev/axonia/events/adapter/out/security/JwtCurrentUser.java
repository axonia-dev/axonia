package dev.axonia.events.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("eventsJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
