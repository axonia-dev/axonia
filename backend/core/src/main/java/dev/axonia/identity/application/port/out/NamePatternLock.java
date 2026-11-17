package dev.axonia.identity.application.port.out;

/**
 * Serialises an Organization's name writes against a save of its name patterns, for the rest of the caller's
 * transaction: a name writer holds it shared from before it reads the patterns, a save holds it exclusive. Every name
 * written then follows either the old list or the new one, never a list a concurrent save has already replaced.
 */
public interface NamePatternLock {

    void shared(String organizationId);

    void exclusive(String organizationId);
}
