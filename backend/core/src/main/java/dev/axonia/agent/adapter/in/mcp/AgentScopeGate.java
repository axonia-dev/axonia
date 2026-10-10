package dev.axonia.agent.adapter.in.mcp;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import dev.axonia.agent.domain.AgentScope;
import dev.axonia.shared.api.error.ForbiddenException;

/** Layer-2 scope enforcement: the token must carry the tool's scope (the member-set ceiling). */
@Component
class AgentScopeGate {

    void require(AgentScope scope) {
        if (!currentScopes().contains(scope.code())) {
            throw new ForbiddenException("This token lacks the '" + scope.code() + "' scope; grant it when minting.");
        }
    }

    private List<String> currentScopes() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            var scopes = jwt.getToken().getClaimAsStringList("scopes");
            return scopes == null ? List.of() : scopes;
        }
        return List.of();
    }
}
