package dev.axonia.events.api;

/**
 * Closing a past session as held in one step: the community hears a wrap-up of it or not, and its recording and notes
 * are recorded with it. Either all of it lands or none of it does, so a refused close can be retried as it was.
 *
 * <p>
 * Only the held answer is offered. A session that never took place is not history to record: a one-off one is deleted,
 * and a series occurrence is answered on the session page.
 */
public interface SessionClosure {

    Session closeAsHeld(Long sessionId, SessionClosureRequest request);
}
