package dev.axonia.communication.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.communication.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("communicationJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {
    @Override
    public String userId() {
        return CurrentToken.subject();
    }
}
