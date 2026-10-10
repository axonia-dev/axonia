package dev.axonia.knowledge.adapter.out.security;

import org.springframework.stereotype.Component;

import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.shared.api.security.CurrentToken;

@Component("knowledgeJwtCurrentUser")
class JwtCurrentUser implements CurrentUser {

    @Override
    public String userId() {

        return CurrentToken.subject();
    }
}
