package dev.axonia.mentoring.api;

/**
 * Whether the member has a reason to open My mentoring: they sit in a guild taking mentoring, or are on a proposal or
 * pairing of any state. It carries nothing of what My mentoring lists.
 */
public record MentoringPresence(boolean inAMentoringGuild) {
}
