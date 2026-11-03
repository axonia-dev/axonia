package dev.axonia.events.api;

/**
 * Removes a one-off session that never took place, as if it had never been scheduled. A held session is history and
 * stays; an occurrence of a series is skipped or cancelled instead, since its series would only project it again.
 */
public interface SessionDeletion {

    void delete(Long sessionId);
}
