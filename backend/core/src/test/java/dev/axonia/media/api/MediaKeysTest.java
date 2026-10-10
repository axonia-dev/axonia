package dev.axonia.media.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaKeysTest {

    private static final String FILE = "3f2b8c1e-5d4a-4b6c-9e8f-0a1b2c3d4e5f";

    @Test
    void everyKeyTheBackendMintsIsServable() {

        // when
        var avatar = MediaKeys.avatar("01j9z3k8q2w4e6r8t0y2u4i6o8", "image/png");
        var reaction = MediaKeys.reaction("acme-corp", "image/webp");
        var badge = MediaKeys.communityAsset(42L, "badge", "image/jpeg");
        var hero = MediaKeys.communityAsset(42L, "hero", "image/png");

        // then
        assertThat(avatar).startsWith("avatars/01j9z3k8q2w4e6r8t0y2u4i6o8/").endsWith(".png");
        assertThat(reaction).startsWith("reactions/acme-corp/").endsWith(".webp");
        assertThat(badge).startsWith("communities/42/badge/").endsWith(".jpg");
        assertThat(hero).startsWith("communities/42/hero/").endsWith(".png");
        assertThat(new String[]{avatar, reaction, badge, hero}).allMatch(MediaKeys::isServable);
    }

    @ParameterizedTest
    @ValueSource(strings = {"avatars/acme/" + FILE + ".png", "avatars/acme.example_1~x/" + FILE + ".jpg",
            "reactions/default/" + FILE + ".webp", "communities/7/badge/" + FILE + ".png",
            "communities/7/hero/" + FILE + ".jpg"})
    void aKeyInAShapeTheBackendWritesIsServable(String key) {

        assertThat(MediaKeys.isServable(key)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "avatars", "avatars/acme/photo.png", "avatars/acme/" + FILE + ".svg",
            "avatars/acme/" + FILE + ".html", "avatars/acme/" + FILE + ".PNG", "avatars/acme/" + FILE,
            "avatars/" + FILE + ".png", "avatars//" + FILE + ".png", "avatars/../" + FILE + ".png",
            "avatars/./" + FILE + ".png", "avatars/.hidden/" + FILE + ".png", "avatars/acme/extra/" + FILE + ".png",
            "avatars/ac me/" + FILE + ".png", "avatars/acme%2F..%2Fx/" + FILE + ".png",
            "/avatars/acme/" + FILE + ".png", "avatars/acme/" + FILE + ".png/", "exports/acme/" + FILE + ".png",
            "communities/abc/badge/" + FILE + ".png", "communities/7/logo/" + FILE + ".png",
            "communities/7/" + FILE + ".png", "communities/-7/badge/" + FILE + ".png", "Avatars/acme/" + FILE + ".png",
            "avatars/acme/" + FILE + ".png\n"})
    void anyOtherKeyIsNotServable(String key) {

        assertThat(MediaKeys.isServable(key)).isFalse();
    }

    @Test
    void aKeyLongerThanTheLongestColumnHoldsIsNotServable() {

        // given
        var key = "avatars/" + "a".repeat(MediaKeys.MAX_LENGTH) + "/" + FILE + ".png";

        // when / then
        assertThat(MediaKeys.isServable(key)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"acme corp", "acme/corp", "..", ".acme", "", "acmé"})
    void anOrganizationIdThatCouldNotBeServedFailsTheUploadInsteadOfStoringAnUnreachableImage(String organizationId) {

        assertThatThrownBy(() -> MediaKeys.avatar(organizationId, "image/png"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("avatars/" + organizationId);
    }
}
