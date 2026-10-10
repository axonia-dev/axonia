package dev.axonia.agent.adapter.in.web;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.agent.application.AuthorizationOutcome;
import dev.axonia.agent.application.AuthorizationRequest;
import dev.axonia.agent.application.McpAuthorization;
import dev.axonia.agent.configuration.AgentSecurityConfig;
import dev.axonia.identity.api.connection.AgentConnections;
import dev.axonia.identity.api.connection.ConnectionSummary;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/** The caller's own MCP connections, and the consent step that creates one. */
@RestController
@RequestMapping("/${spring.application.name}/me/agent-connections")
@RequiredArgsConstructor
class AgentConnectionController {

    private final McpAuthorization authorization;
    private final AgentConnections connections;
    private final AgentSecurityConfig.McpRateLimiters limiters;

    /** {@code organizationId} is the organization the consent card showed, so a token switched underneath is caught. */
    record Approval(@NotNull AuthorizationRequest request, @NotNull Set<String> scopes,
            @NotBlank String organizationId) {
    }

    @GetMapping
    List<ConnectionSummary> list() {
        return connections.list(CurrentToken.subject(), CurrentToken.organization());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect(@PathVariable Long id) {
        connections.revoke(CurrentToken.subject(), CurrentToken.organization(), id);
    }

    @PostMapping("/authorization-requests")
    ResponseEntity<AuthorizationOutcome> review(@RequestBody AuthorizationRequest request) {
        return limited(() -> authorization.review(request));
    }

    @PostMapping("/authorization-requests/approve")
    ResponseEntity<AuthorizationOutcome> approve(@Valid @RequestBody Approval approval) {

        return limited(() -> {
            if (CurrentToken.hasOrganization() && !approval.organizationId().equals(CurrentToken.organization())) {
                throw new ConflictException("ORGANIZATION_CHANGED",
                        "The active organization changed after this request was shown.");
            }
            return authorization.approve(approval.request(), approval.scopes());
        });
    }

    @PostMapping("/authorization-requests/deny")
    ResponseEntity<AuthorizationOutcome> deny(@RequestBody AuthorizationRequest request) {
        return limited(() -> authorization.deny(request));
    }

    /** Per person, and answered like the token endpoint's refusal: 429, {@code Retry-After}, nothing cached. */
    private ResponseEntity<AuthorizationOutcome> limited(Supplier<AuthorizationOutcome> handler) {

        var decision = limiters.consentRequests().tryConsume(CurrentToken.subject());
        if (!decision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()))
                    .cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok(handler.get());
    }
}
