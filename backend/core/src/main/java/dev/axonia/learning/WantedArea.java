package dev.axonia.learning;

/**
 * An area members want to learn, named as the community reads it: how many want it, and how many people a search for it
 * would list to ask.
 */
public record WantedArea(String tag, int learners, int canBeAsked) {
}
