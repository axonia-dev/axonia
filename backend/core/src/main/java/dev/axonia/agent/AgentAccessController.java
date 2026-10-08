package dev.axonia.agent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Whether the caller's organization may connect agents, so the app can drop the API keys surface and the documentation
 * can say why, without re-deriving the rule in the frontend.
 *
 * <p>
 * Its own controller rather than a method on {@code PersonalTokenController}: that one is mapped at {@code /me/tokens}
 * and a sibling path there would read as a token named {@code agent-access}.
 *
 * <p>
 * A boolean, not a reason. The absence renders identically whether the cause is the organization's kind today or an
 * unpaid plan later, and a reason string would be a second place for the rule to live.
 */
@RestController
@RequestMapping("/${spring.application.name}/me/agent-access")
@RequiredArgsConstructor
public class AgentAccessController {

    private final AgentAccess access;

    public record AgentAccessView(boolean available) {
    }

    @GetMapping
    AgentAccessView agentAccess() {

        return new AgentAccessView(access.isAvailableToCaller());
    }
}
