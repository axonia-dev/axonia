package dev.axonia.agent.internal;

import java.time.Clock;
import java.util.function.Supplier;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import dev.axonia.ActionableForbiddenException;
import dev.axonia.ConflictException;
import dev.axonia.CurrentToken;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.agent.AgentScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The single path every tool runs through: scope (layer 2), execute, audit, and map domain errors to clean messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AgentToolSupport {

    private final AgentScopeGate scopeGate;
    private final AgentToolCallRepository audit;
    private final Clock clock;

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
        try {
            audit.save(AgentToolCallEntity.builder().organizationId(CurrentToken.organization())
                    .userId(CurrentToken.subject()).tokenId(credentialClaim("tokenId"))
                    .connectionId(credentialClaim("connectionId")).toolName(toolName).outcome(outcome)
                    .occurredAt(clock.instant()).build());
        } catch (RuntimeException e) {
            // auditing must never fail the tool call; log the hole in the audit trail and swallow
            log.warn("Failed to record agent_tool_call audit row for tool {}: {}", toolName, e.getMessage(), e);
        }
    }

    private Long credentialClaim(String name) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaim(name) instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
