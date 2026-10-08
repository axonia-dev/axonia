package dev.axonia.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTypesTest {

    @Test
    void extensionFor_knownTypes() {

        // then
        assertThat(MediaTypes.extensionFor("image/png")).isEqualTo(".png");
        assertThat(MediaTypes.extensionFor("image/webp")).isEqualTo(".webp");
    }

    @Test
    void extensionFor_anythingElse_defaultsToJpg() {

        // then
        assertThat(MediaTypes.extensionFor("image/jpeg")).isEqualTo(".jpg");
        assertThat(MediaTypes.extensionFor("application/octet-stream")).isEqualTo(".jpg");
    }

    @ParameterizedTest
    @CsvSource({"avatars/a/x.png,image/png", "avatars/a/x.webp,image/webp", "avatars/a/x.jpg,image/jpeg"})
    void theContentTypeIsReadFromTheKeysExtension(String key, String expected) {

        assertThat(MediaTypes.contentTypeFor(key)).isEqualTo(expected);
    }
}
