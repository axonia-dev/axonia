package dev.axonia.events;

/**
 * How many of a community's members read one clock.
 *
 * <p>
 * Counts only, never ids. An organizer needs to know which way to move an hour, which zones and counts answer in full;
 * naming the members would turn each person's whereabouts into something a colleague reads off a screen rather than
 * something they told anyone.
 */
public record MemberZone(String zone, int count) {
}
