package dev.axonia.mentoring.api;

/** What the caller offers or seeks in one community; {@code menteeLimit} is null for no limit. */
public record Availability(boolean takingMentees, Integer menteeLimit, boolean lookingForMentor) {
}
