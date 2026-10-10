package dev.axonia.expertise.domain;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.expertise.api.CommunityAreas;

import static org.assertj.core.api.Assertions.assertThat;

class AliasResolverTest {

    @Test
    void canonicalOf_resolvesKnownVariant() {

        var map = Map.of("apache-kafka", "kafka");

        assertThat(AliasResolver.canonicalOf("apache-kafka", map)).isEqualTo("kafka");
    }

    @Test
    void canonicalOf_returnsIdentityWhenUnmapped() {

        var map = Map.of("apache-kafka", "kafka");

        assertThat(AliasResolver.canonicalOf("kafka", map)).isEqualTo("kafka");
    }

    @Test
    void variantsOf_returnsCanonicalPlusAllVariants() {

        var map = Map.of("apache-kafka", "kafka");

        assertThat(AliasResolver.variantsOf("kafka", map)).containsExactlyInAnyOrder("kafka", "apache-kafka");
    }

    @Test
    void variantsOf_returnsJustItselfWhenNoVariants() {

        assertThat(AliasResolver.variantsOf("postgres", Map.of())).containsExactly("postgres");
    }

    @Test
    void variantsOf_ignoresVariantsForOtherCanonicals() {

        // "spring-boot" points to "spring"; asking for variants of "kafka" should not include "spring-boot"
        var map = Map.of("spring-boot", "spring");

        assertThat(AliasResolver.variantsOf("kafka", map)).containsExactly("kafka");
    }

    @ParameterizedTest
    @ValueSource(strings = {"kafka", "apache-kafka", "spring", "spring-boot", "postgres"})
    void answersExactlyWhatTheCommunitysAreasAnswer(String tag) {

        var map = Map.of("apache-kafka", "kafka", "confluent", "kafka", "spring-boot", "spring");
        var areas = new CommunityAreas(map);

        assertThat(AliasResolver.canonicalOf(tag, map)).isEqualTo(areas.canonicalOf(tag));
        assertThat(AliasResolver.variantsOf(tag, map)).isEqualTo(areas.variantsOf(tag));
    }
}
