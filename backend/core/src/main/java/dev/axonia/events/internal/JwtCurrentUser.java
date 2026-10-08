package dev.axonia.events.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.events.CurrentUser;

@Component("eventsJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
