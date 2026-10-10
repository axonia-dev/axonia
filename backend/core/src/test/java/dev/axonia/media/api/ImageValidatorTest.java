package dev.axonia.media.api;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x01, 0x02};

    @Test
    void accepts_validPng() {

        // given
        var upload = new ImageUpload(PNG, "image/png", "logo.png");

        // when / then
        assertThatCode(() -> ImageValidator.validate(upload, 2L * 1024 * 1024)).doesNotThrowAnyException();
    }

    @Test
    void rejects_emptyContent() {

        // given
        var upload = new ImageUpload(new byte[0], "image/png", "logo.png");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 2L * 1024 * 1024))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_IMAGE");
    }

    @Test
    void rejects_unsupportedContentType() {

        // given
        var upload = new ImageUpload(PNG, "image/svg+xml", "logo.svg");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 2L * 1024 * 1024))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void rejects_tooLarge() {

        // given
        var upload = new ImageUpload(PNG, "image/png", "logo.png");

        // when / then - a 4-byte cap is smaller than the content
        assertThatThrownBy(() -> ImageValidator.validate(upload, 4L)).isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("FILE_TOO_LARGE");
    }

    /**
     * The reaction picker's own bound: 128 KB used to divide by {@code 1024 * 1024} unconditionally and report "0 MB",
     * a sentence nobody could act on.
     */
    @Test
    void rejects_tooLarge_rendersKilobytesBelowOneMegabyte() {

        // given
        var upload = new ImageUpload(new byte[128 * 1024 + 1], "image/png", "big.png");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 128L * 1024)).isInstanceOf(ValidationException.class)
                .hasMessage("Image exceeds the maximum size of 128 KB");
    }

    /** Exactly the 1 MB boundary: "at or above it" renders MB, not "1024 KB". */
    @Test
    void rejects_tooLarge_rendersMegabytesAtOrAboveOneMegabyte() {

        // given
        var upload = new ImageUpload(new byte[1024 * 1024 + 1], "image/png", "big.png");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 1024L * 1024)).isInstanceOf(ValidationException.class)
                .hasMessage("Image exceeds the maximum size of 1 MB");
    }

    /** The avatar/badge bound this message already rendered correctly, kept exact after the fix. */
    @Test
    void rejects_tooLarge_avatarBoundStillRendersWholeMegabytes() {

        // given
        var upload = new ImageUpload(new byte[2 * 1024 * 1024 + 1], "image/png", "big.png");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 2L * 1024 * 1024))
                .isInstanceOf(ValidationException.class).hasMessage("Image exceeds the maximum size of 2 MB");
    }

    /** A non-whole limit keeps its fraction rather than being truncated away or padded with a trailing ".0". */
    @Test
    void rejects_tooLarge_rendersAFractionalLimitWithoutTruncatingIt() {

        // given - a 1.5 KB cap
        var upload = new ImageUpload(new byte[2000], "image/png", "big.png");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 1536L)).isInstanceOf(ValidationException.class)
                .hasMessage("Image exceeds the maximum size of 1.5 KB");
    }

    @Test
    void rejects_contentNotMatchingDeclaredType() {

        // given - declared png but bytes are not a png header
        var upload = new ImageUpload(new byte[]{0x00, 0x01, 0x02, 0x03}, "image/png", "logo.png");

        // when / then
        assertThatThrownBy(() -> ImageValidator.validate(upload, 2L * 1024 * 1024))
                .isInstanceOf(ValidationException.class).extracting("code").isEqualTo("INVALID_IMAGE");
    }
}
