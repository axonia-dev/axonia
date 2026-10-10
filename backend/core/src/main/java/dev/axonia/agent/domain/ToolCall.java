package dev.axonia.agent.domain;

import java.time.Instant;

import lombok.Builder;

/** One agent tool invocation as the audit trail records it. */
@Builder
public record ToolCall(String organizationId, String userId, Long tokenId, Long connectionId, String toolName,
        String outcome, Instant occurredAt) {
}
