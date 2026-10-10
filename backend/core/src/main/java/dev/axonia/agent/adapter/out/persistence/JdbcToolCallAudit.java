package dev.axonia.agent.adapter.out.persistence;

import org.springframework.stereotype.Component;

import dev.axonia.agent.application.port.out.ToolCallAudit;
import dev.axonia.agent.domain.ToolCall;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcToolCallAudit implements ToolCallAudit {

    private final AgentToolCallRepository repository;

    @Override
    public void record(ToolCall call) {

        repository.save(toEntity(call));
    }

    static AgentToolCallEntity toEntity(ToolCall call) {

        return AgentToolCallEntity.builder().organizationId(call.organizationId()).userId(call.userId())
                .tokenId(call.tokenId()).connectionId(call.connectionId()).toolName(call.toolName())
                .outcome(call.outcome()).occurredAt(call.occurredAt()).build();
    }
}
