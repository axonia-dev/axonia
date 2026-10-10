package dev.axonia.events.api;

/**
 * A read-time view over the topic lifecycle keyed by event date. Only {@code PAST} — topics on a session whose date has
 * passed, plus done topics — needs a dedicated query; backlog and upcoming are served by the existing status filters.
 */
public enum TopicPhase {
    PAST
}
