package dev.axonia.media.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import dev.axonia.media.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** What every store behind {@link MediaStorage} does, run against each adapter's real store. */
abstract class MediaStorageContract {

    private static final String KEY = "avatars/acme/3f2b8c1e-5d4a-4b6c-9e8f-0a1b2c3d4e5f.png";

    abstract MediaStorage sut();

    @Test
    void whatIsStoredIsReadBackWithItsLength() throws IOException {

        // given
        var bytes = new byte[]{1, 2, 3, 4, 5};
        sut().store(KEY, new ByteArrayInputStream(bytes), "image/png", bytes.length);

        // when
        var actual = sut().open(KEY).orElseThrow();

        // then
        try (var stream = actual.stream()) {
            assertThat(stream.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(actual.length()).isEqualTo(5L);
    }

    @Test
    void storingAgainUnderTheSameKeyReplacesTheObject() throws IOException {

        // given
        sut().store(KEY, new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/png", 3);

        // when
        sut().store(KEY, new ByteArrayInputStream(new byte[]{9}), "image/png", 1);

        // then
        try (var stream = sut().open(KEY).orElseThrow().stream()) {
            assertThat(stream.readAllBytes()).containsExactly(9);
        }
    }

    @Test
    void aKeyNobodyStoredOpensAsEmpty() {

        // when / then
        assertThat(sut().open("avatars/acme/00000000-0000-0000-0000-000000000000.png")).isEmpty();
    }

    @Test
    void aDeletedObjectOpensAsEmpty() {

        // given
        sut().store(KEY, new ByteArrayInputStream(new byte[]{1}), "image/png", 1);

        // when
        sut().delete(KEY);

        // then
        assertThat(sut().open(KEY)).isEmpty();
    }

    @Test
    void theLengthIsKnownWithoutOpeningTheObject() {

        // given
        sut().store(KEY, new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/png", 3);

        // when
        var actual = sut().length(KEY);

        // then
        assertThat(actual).contains(3L);
    }

    @Test
    void aKeyNobodyStoredHasNoLength() {

        // when / then
        assertThat(sut().length("avatars/acme/22222222-2222-2222-2222-222222222222.png")).isEmpty();
    }

    @Test
    void deletingAKeyNobodyStoredIsNotAnError() {

        // when / then
        assertThatCode(() -> sut().delete("avatars/acme/11111111-1111-1111-1111-111111111111.png"))
                .doesNotThrowAnyException();
    }
}
