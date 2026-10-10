package dev.axonia.shared.api.personaldata;

import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code section()} is the anchor the privacy notice and its build-time gate match on - a cross-artifact contract with
 * the manifest a later task adds. Deriving the expected slug from {@code name()} rather than restating the table of
 * constants means a typo in a future constant's slug fails here, which a copy of the table would not catch.
 */
class DataCategoryTest {

    @Test
    void sectionIsTheLowercaseHyphenatedFormOfTheConstantName() {

        assertThat(DataCategory.values()).allSatisfy(category -> assertThat(category.section())
                .isEqualTo(category.name().toLowerCase(Locale.ROOT).replace('_', '-')));
    }

    @Test
    void everySectionSlugIsDistinct() {

        var slugs = Arrays.stream(DataCategory.values()).map(DataCategory::section).distinct().count();

        assertThat(slugs).isEqualTo(DataCategory.values().length);
    }
}
