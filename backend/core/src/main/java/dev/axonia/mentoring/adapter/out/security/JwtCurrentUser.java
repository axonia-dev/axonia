package dev.axonia.mentoring.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("mentoringJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }

    @Override
    public boolean delegated() {

        return CurrentToken.isDelegated();
    }
}
