package dev.axonia.expertise.api;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityAreasTest {

    private final CommunityAreas areas = new CommunityAreas(
            Map.of("k8s", "kubernetes", "kube", "kubernetes", "kafak", "kafka"));

    @Test
    void aVariantReadsAsItsCanonical() {

        assertThat(areas.canonicalOf("k8s")).isEqualTo("kubernetes");
    }

    @Test
    void aNameWithNoMergeReadsAsItself() {

        assertThat(areas.canonicalOf("kafka")).isEqualTo("kafka");
    }

    @Test
    void anAreasSpellingsAreItsCanonicalAndEveryVariant() {

        assertThat(areas.variantsOf("kubernetes")).containsExactlyInAnyOrder("kubernetes", "k8s", "kube");
    }

    @Test
    void anAreasSpellingsLeaveOutAnotherAreasVariants() {

        assertThat(areas.variantsOf("kafka")).containsExactlyInAnyOrder("kafka", "kafak");
    }
}
