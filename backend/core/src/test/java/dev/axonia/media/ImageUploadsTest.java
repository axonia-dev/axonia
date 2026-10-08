package dev.axonia.media;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import dev.axonia.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadsTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};

    @Test
    void carriesTheBytesTypeAndFilenameOfThePart() {

        // given
        var part = new MockMultipartFile("file", "logo.png", "image/png", PNG);

        // when
        var upload = ImageUploads.from(part);

        // then
        assertThat(upload.content()).isEqualTo(PNG);
        assertThat(upload.contentType()).isEqualTo("image/png");
        assertThat(upload.originalFilename()).isEqualTo("logo.png");
    }

    /** The one failure a controller cannot do anything about, turned into a refusal the member can read. */
    @Test
    void anUnreadablePartIsRejected() {

        // given
        var broken = new MockMultipartFile("file", "logo.png", "image/png", PNG) {

            @Override
            public byte[] getBytes() throws IOException {

                throw new IOException("simulated disk error");
            }
        };

        // when / then
        assertThatThrownBy(() -> ImageUploads.from(broken)).isInstanceOf(ValidationException.class).extracting("code")
                .isEqualTo("INVALID_IMAGE");
    }
}
