package dev.axonia.expertise.domain;

/**
 * Strongest first. {@link #OPEN_TO_QUESTIONS} is not evidence: a person has it only in an area where they declared
 * themselves open to questions and have no signal.
 */
public enum Tier {
    PRACTITIONER, CONTRIBUTOR, INTERESTED, OPEN_TO_QUESTIONS
}
