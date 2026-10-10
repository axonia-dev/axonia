package dev.axonia.agent.adapter.in.mcp;

import java.util.function.Supplier;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import dev.axonia.agent.application.ToolCallRecorder;
import dev.axonia.agent.domain.AgentScope;
import dev.axonia.shared.api.error.ActionableForbiddenException;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * The single path every tool runs through: scope (layer 2), execute, audit, and map domain errors to clean messages.
 */
@Component
@RequiredArgsConstructor
class AgentToolSupport {

    private final AgentScopeGate scopeGate;
    private final ToolCallRecorder recorder;

    <T> T run(String toolName, AgentScope scope, Supplier<T> body) {
        try {
            scopeGate.require(scope);
        } catch (ForbiddenException e) {
            record(toolName, "SCOPE_DENIED");
            throw new AgentToolException(e.getMessage());
        } catch (RuntimeException e) {
            // any other gate failure must still be audited and must never leak internals to the MCP client
            record(toolName, "SCOPE_ERROR");
            throw new AgentToolException("The action could not be completed.");
        }
        try {
            var result = body.get();
            record(toolName, "OK");
            return result;
        } catch (RuntimeException e) {
            record(toolName, "DOMAIN_ERROR");
            throw new AgentToolException(mapMessage(e));
        }
    }

    private String mapMessage(RuntimeException e) {
        // Checked before the ForbiddenException branch below, which is the sanitizer: this refusal's message is the
        // point of it. Told only "you are not a member", an agent that credited a colleague would rationally call
        // community.join next, on a community it is already in, instead of dropping the field and retrying.
        if (e instanceof ActionableForbiddenException) {
            return e.getMessage();
        }
        if (e instanceof ForbiddenException) {
            return "You are not a member of this community, or not permitted to do this.";
        }
        if (e instanceof NotFoundException) {
            return "That item was not found.";
        }
        if (e instanceof ConflictException) {
            return e.getMessage(); // domain conflict messages are already agent-safe (e.g. "already going")
        }
        if (e instanceof ValidationException) {
            return e.getMessage();
        }
        return "The action could not be completed.";
    }

    private void record(String toolName, String outcome) {
        recorder.record(toolName, outcome, credentialClaim("tokenId"), credentialClaim("connectionId"));
    }

    private Long credentialClaim(String name) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaim(name) instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
