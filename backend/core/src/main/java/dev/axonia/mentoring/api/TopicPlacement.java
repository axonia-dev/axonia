package dev.axonia.mentoring.api;

/** Where a topic goes on the board: {@code state} is TODO, IN_PROGRESS or DONE; a null position means last. */
public record TopicPlacement(String state, Integer position) {
}
