package dev.axonia.agent.application.port.out;

import dev.axonia.agent.domain.ToolCall;

public interface ToolCallAudit {

    void record(ToolCall call);
}
