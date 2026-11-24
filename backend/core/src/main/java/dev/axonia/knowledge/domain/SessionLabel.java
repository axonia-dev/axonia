package dev.axonia.knowledge.domain;

import java.time.LocalDate;

import lombok.Builder;

/**
 * A session as the library heads it: its name, its series ({@code null} for a one-off) and the day it was held on.
 * Resolved from the events context per page, so a library never needs the community's whole session history to label
 * the few sessions on screen.
 */
@Builder
public record SessionLabel(String name, String seriesTitle, LocalDate heldOn) {
}
