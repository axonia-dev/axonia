package dev.axonia.recognition.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("recognitionJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
