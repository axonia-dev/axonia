package dev.axonia.learning.api;

import java.util.List;

/**
 * A community's Learning page for the caller: whether they may add, the cap, the areas they hold, and what the
 * community wants to learn, most wanted first.
 */
public record LearningPage(boolean member, boolean archived, int limit, List<HeldArea> mine, List<WantedArea> wanted) {
}
