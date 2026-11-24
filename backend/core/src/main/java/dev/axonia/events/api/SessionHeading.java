package dev.axonia.events.api;

import java.time.LocalDate;

import lombok.Builder;

/**
 * How a session is headed wherever another module shows it: its name, the series it belongs to ({@code null} for a
 * one-off), and the day it is held on, read on the session's own clock.
 */
@Builder
public record SessionHeading(String name, String seriesTitle, LocalDate heldOn) {
}
