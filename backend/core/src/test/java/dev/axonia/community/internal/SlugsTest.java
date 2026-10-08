package dev.axonia.community.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void slugify() {

        // when
        var actual = Slugs.slugify("Frontend Guild");

        // then
        assertThat(actual).isEqualTo("frontend-guild");
    }

    @Test
    void slugify_stripsDiacriticsAndSymbols() {

        // when
        var actual = Slugs.slugify("Café  & Crème!!");

        // then
        assertThat(actual).isEqualTo("cafe-creme");
    }

    @Test
    void slugify_truncatesToMaxLength() {

        // given
        var longName = "a".repeat(200);

        // when
        var actual = Slugs.slugify(longName);

        // then
        assertThat(actual).hasSize(120);
    }

    @Test
    void slugify_fallsBackWhenEmpty() {

        // when
        var actual = Slugs.slugify("***");

        // then
        assertThat(actual).isEqualTo("community");
    }
}
