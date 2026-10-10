package dev.axonia.learning.domain;

/** A community resolved from its slug, with whether it is archived. */
public record LearningCommunity(long id, boolean archived) {
}
