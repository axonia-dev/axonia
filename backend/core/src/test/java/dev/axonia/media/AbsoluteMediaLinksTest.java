package dev.axonia.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbsoluteMediaLinksTest {

    @Test
    void anAddressReadOutsideTheApplicationIsAbsolute() {

        // given
        var sut = new AbsoluteMediaLinks("https://app.example.test/public/media");

        // when
        var actual = sut.urlFor("communities/2/badge/x.png");

        // then
        assertThat(actual).isEqualTo("https://app.example.test/public/media/communities/2/badge/x.png");
    }
}
