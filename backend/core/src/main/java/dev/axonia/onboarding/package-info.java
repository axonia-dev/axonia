/**
 * One person's one onboarding path: which of the two product-authored guides they were given, the community it is
 * about, and how far through it they got.
 *
 * <p>
 * Rooted at the person rather than at any Organization. A row is scoped by {@code app.current_subject} rather than
 * {@code app.current_org}, outlives any one hub, and is never touched by an Organization's own closure: this module
 * implements no {@link dev.axonia.shared.api.personaldata.OrganizationDataPurger}.
 *
 * <p>
 * The three ports below are read once, at assignment, and never again. What they returned is frozen onto the steps as
 * plain strings, the same snapshot argument recognition makes for a recognised contribution.
 */
@ApplicationModule(displayName = "Onboarding")
package dev.axonia.onboarding;

import org.springframework.modulith.ApplicationModule;
