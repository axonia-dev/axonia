package dev.axonia.recognition.application;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.media.api.ImageHeaders;
import dev.axonia.media.api.ImageUpload;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ReactionImagesTest {

    private static final String ORG = "acme";

    @Mock
    private MediaStorage mediaStorage;

    private ReactionImages images;

    @BeforeEach
    void setUp() {

        images = new ReactionImages(mediaStorage);
    }

    @Test
    void storingUploadsAndReturnsTheKey() {

        // when
        var stored = images.store(ORG, new ImageUpload(pngBytes(), "image/png", "ship.png"));

        // then
        assertThat(stored).startsWith("reactions/" + ORG + "/").endsWith(".png");
    }

    @Test
    void storingRefusesAnImageTallerThanTheEdge() {

        // given
        var upload = new ImageUpload(ImageHeaders.webpLossless(256, 257), "image/webp", "tall.webp");

        // when / then
        assertThatThrownBy(() -> images.store(ORG, upload)).isInstanceOf(ValidationException.class).extracting("code")
                .isEqualTo("IMAGE_TOO_LARGE");
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void storingAcceptsAnImageExactlyAtTheEdge() {

        // when
        var stored = images.store(ORG, new ImageUpload(ImageHeaders.webpLossless(256, 256), "image/webp", "a.webp"));

        // then
        assertThat(stored).endsWith(".webp");
    }

    @Test
    void deletingWithNoKeyDoesNothing() {

        images.deleteBlob(null);

        verifyNoInteractions(mediaStorage);
    }

    @Test
    void deletingAnExistingKeyCallsStorageDelete() {

        images.deleteBlob("reactions/" + ORG + "/x.png");

        verify(mediaStorage).delete("reactions/" + ORG + "/x.png");
    }

    @Test
    void aFailedDeleteIsSwallowedRatherThanFailingTheCall() {

        doThrow(new RuntimeException("object store down")).when(mediaStorage).delete("reactions/" + ORG + "/x.png");

        assertThatCode(() -> images.deleteBlob("reactions/" + ORG + "/x.png")).doesNotThrowAnyException();
    }

    /** A 1x1 PNG: the magic bytes are what ImageValidator checks, so a real header is required. */
    private static byte[] pngBytes() {

        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }
}
