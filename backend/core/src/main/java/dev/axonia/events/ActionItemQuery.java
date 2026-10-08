package dev.axonia.events;

/**
 * Query-string filters for the action items list endpoint, bound from request params. {@code outcome} narrows the list
 * to one lifecycle bucket; omitted, every outcome is returned. {@code originSessionId} narrows it to the items raised
 * at one session - history, never current state. {@code checkpointSessionId} narrows it to the items one session must
 * answer for: pinned to it, or unpinned and resolving to it as the community's soonest still-upcoming session. Every
 * filter is optional and they compose.
 */
public record ActionItemQuery(ActionItemOutcome outcome, Long originSessionId, Long checkpointSessionId) {
}
