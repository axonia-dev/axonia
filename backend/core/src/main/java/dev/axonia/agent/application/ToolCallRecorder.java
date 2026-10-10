package dev.axonia.agent.application;

import java.time.Clock;

import org.springframework.stereotype.Service;

import dev.axonia.agent.application.port.out.ToolCallAudit;
import dev.axonia.agent.domain.ToolCall;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Records the outcome of one agent tool call for the calling member, never failing the call it records. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallRecorder {

    private final ToolCallAudit audit;
    private final Clock clock;

    public void record(String toolName, String outcome, Long tokenId, Long connectionId) {

        try {
            audit.record(ToolCall.builder().organizationId(CurrentToken.organization()).userId(CurrentToken.subject())
                    .tokenId(tokenId).connectionId(connectionId).toolName(toolName).outcome(outcome)
                    .occurredAt(clock.instant()).build());
        } catch (RuntimeException e) {
            log.warn("Failed to record agent_tool_call audit row for tool {}: {}", toolName, e.getMessage(), e);
        }
    }
}
