package dev.axonia.communication.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.communication.CurrentUser;

@Component("communicationJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {
    @Override
    public String userId() {
        return CurrentToken.subject();
    }
}
