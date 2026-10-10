package dev.axonia.learning.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.learning.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("learningJwtCurrentUser")
public class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
