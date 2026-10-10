package dev.axonia.expertise.application;

import java.util.List;

/**
 * Every contribution the map draws, with its authors lifted out of the rows because one person appears in dozens of
 * them. {@code truncated} says the cap was hit: the picture is thinner than the practice, and no number the page states
 * comes from here, so nothing it reports is wrong.
 */
public record MapContributions(List<MapPerson> people, List<MapContribution> items, boolean truncated) {
}
