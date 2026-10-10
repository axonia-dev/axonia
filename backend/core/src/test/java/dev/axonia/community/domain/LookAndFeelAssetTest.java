package dev.axonia.community.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.axonia.media.api.MediaKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The media route serves only the key shapes {@link MediaKeys} knows, and that list cannot read this enum: the
 * {@code media} module sits below {@code community}. A new asset whose segment the route would refuse fails here, in
 * the build, rather than at a member's first upload.
 */
class LookAndFeelAssetTest {

    @ParameterizedTest
    @EnumSource(LookAndFeelAsset.class)
    void everyLookAndFeelAssetMintsAServableKey(LookAndFeelAsset asset) {

        // when
        var actual = MediaKeys.communityAsset(1L, asset.segment(), "image/png");

        // then
        assertThat(MediaKeys.isServable(actual)).isTrue();
    }
}
