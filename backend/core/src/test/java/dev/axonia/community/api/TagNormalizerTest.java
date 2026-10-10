package dev.axonia.community.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagNormalizerTest {

    @Test
    void normalize_titleCaseWithSpace() {

        assertThat(TagNormalizer.normalize("Apache Kafka")).isEqualTo("apache-kafka");
    }

    @Test
    void normalize_alreadyLowercaseWithLeadingTrailingSpaces() {

        assertThat(TagNormalizer.normalize(" apache-kafka ")).isEqualTo("apache-kafka");
    }

    @Test
    void normalize_uppercaseWithDoubleSpace() {

        assertThat(TagNormalizer.normalize("APACHE  KAFKA")).isEqualTo("apache-kafka");
    }

    @Test
    void normalize_specialCharRemoved() {

        assertThat(TagNormalizer.normalize("C#")).isEqualTo("c");
    }

    @Test
    void normalize_accentedCharStripped() {

        assertThat(TagNormalizer.normalize("Café")).isEqualTo("cafe");
    }

    @Test
    void normalize_blankInput() {

        assertThat(TagNormalizer.normalize("  ")).isEqualTo("");
    }

    @Test
    void normalize_emptyInput() {

        assertThat(TagNormalizer.normalize("")).isEqualTo("");
    }

    @Test
    void normalize_uppercaseLocaleIndependent() {

        assertThat(TagNormalizer.normalize("INDEX")).isEqualTo("index");
    }
}
