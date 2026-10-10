package dev.axonia.agent.adapter.in.mcp;

/** A clean, agent-facing tool failure. Its message is safe to surface to the caller; no internals leak. */
class AgentToolException extends RuntimeException {
    AgentToolException(String message) {
        super(message);
    }
}
