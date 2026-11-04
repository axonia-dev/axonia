package dev.axonia.events.api;

/**
 * Sets, changes or clears a session's own title, whatever its status: naming a session after the fact, a held one
 * included, is the main reason to. A blank or missing title clears it, and the session is named by
 * {@link dev.axonia.events.domain.SessionName}'s next step again.
 */
public interface SessionNaming {

    Session rename(Long sessionId, String title);
}
