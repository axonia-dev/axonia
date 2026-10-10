package dev.axonia.agent.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Table("agent_tool_call")
class AgentToolCallEntity {

    @Id
    private Long id;

    private String organizationId;
    private String userId;
    private Long tokenId;
    private Long connectionId;
    private String toolName;
    private String outcome;
    private Instant occurredAt;
}
