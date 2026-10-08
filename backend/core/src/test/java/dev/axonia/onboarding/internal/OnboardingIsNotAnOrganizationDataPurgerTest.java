package dev.axonia.onboarding.internal;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import dev.axonia.OrganizationDataPurger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A path outlives any one hub, so closing an Organization must never delete or re-onboard one. This test exists because
 * every other module that owns tables has an {@code OrganizationDataPurger}, and the reflex to add one here - to make
 * this module look like its neighbours - would silently destroy a person's in-progress onboarding the moment a hub was
 * closed, with no record and nothing in the schema left to notice by.
 *
 * <p>
 * Scanned off the classpath rather than asked of a Spring context, so it catches a purger added anywhere in the module
 * rather than only one somebody also remembered to wire into {@code PurgerTestSupport}'s hand-maintained
 * {@code @Import} list. That list is where such a bean would be missing from, not where it would be found.
 *
 * <p>
 * {@code OrganizationDataPurgerCouplingTest} requires every purger to also declare a {@code PersonalDataFootprint} and
 * a {@code PersonalDataExporter}. That is a one-way implication, so this module having the latter two and not the first
 * satisfies it rather than violating it.
 */
class OnboardingIsNotAnOrganizationDataPurgerTest {

    private static final String MODULE_PACKAGE = "dev.axonia.onboarding";

    @Test
    void onboardingIsDeliberatelyNotAnOrganizationDataPurger() {

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(OrganizationDataPurger.class));

        assertThat(scanner.findCandidateComponents(MODULE_PACKAGE))
                .describedAs("a path outlives any one hub, so no class in '%s' may purge on Organization Close",
                        MODULE_PACKAGE)
                .isEmpty();
    }

    @Test
    void theScanWouldSeeAPurgerIfOneWereAdded() {

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(OrganizationDataPurger.class));

        // Proves the scan and filter are live, against a package known to contain a purger. Without this, an
        // AssignableTypeFilter that matched nothing would let the assertion above pass just as happily.
        assertThat(scanner.findCandidateComponents("dev.axonia.learning")).isNotEmpty();
    }
}
