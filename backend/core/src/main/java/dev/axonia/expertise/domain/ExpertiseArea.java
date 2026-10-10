package dev.axonia.expertise.domain;

import java.util.List;

/**
 * One area of the community, and how many distinct people can be asked about it: everyone a search for it would list,
 * whether their claim is a signal or a declaration, counted once each.
 */
public record ExpertiseArea(String canonicalTag, List<String> variants, int peopleCount) {
}
