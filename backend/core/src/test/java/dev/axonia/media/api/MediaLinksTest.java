package dev.axonia.media.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaLinksTest {

    @Test
    void anAddressForTheApplicationsOwnPagesIsAPathOnTheirOrigin() {

        // given
        var sut = new MediaLinks("/public/media");

        // when
        var actual = sut.urlFor("communities/2/hero/x.jpg");

        // then
        assertThat(actual).isEqualTo("/public/media/communities/2/hero/x.jpg");
    }
}
