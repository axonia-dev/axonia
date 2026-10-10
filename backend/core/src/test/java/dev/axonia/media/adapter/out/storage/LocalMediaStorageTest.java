package dev.axonia.media.adapter.out.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.axonia.media.api.MediaStorage;
import dev.axonia.media.api.MediaStorageContract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMediaStorageTest extends MediaStorageContract {

    @TempDir
    Path tempDir;

    @Override
    protected MediaStorage sut() {

        return new LocalMediaStorage(tempDir.toString());
    }

    @Test
    void store_writesContentUnderBasePath() throws IOException {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());
        var bytes = "hello".getBytes();

        // when
        sut.store("logo.png", new ByteArrayInputStream(bytes), "image/png", bytes.length);

        // then
        assertThat(Files.readAllBytes(tempDir.resolve("logo.png"))).isEqualTo(bytes);
    }

    @Test
    void store_createsNestedDirectoriesForKeyWithSlashes() throws IOException {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());
        var bytes = "badge".getBytes();

        // when
        sut.store("communities/1/badge/x.png", new ByteArrayInputStream(bytes), "image/png", bytes.length);

        // then
        assertThat(Files.readAllBytes(tempDir.resolve("communities/1/badge/x.png"))).isEqualTo(bytes);
    }

    @Test
    void store_wrapsIoExceptionAsUnchecked() {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated failure");
            }
        };

        // when / then
        assertThatThrownBy(() -> sut.store("fail.png", broken, "image/png", 0))
                .isInstanceOf(java.io.UncheckedIOException.class).hasMessageContaining("fail.png");
    }

    @Test
    void delete_wrapsIoExceptionAsUnchecked() throws IOException {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());
        var locked = tempDir.resolve("locked");
        Files.createDirectories(locked);
        Files.write(locked.resolve("file.png"), "x".getBytes());
        locked.toFile().setWritable(false);

        try {
            // when / then
            assertThatThrownBy(() -> sut.delete("locked/file.png")).isInstanceOf(java.io.UncheckedIOException.class);
        } finally {
            locked.toFile().setWritable(true);
        }
    }

    @Test
    void store_rejectsATraversalKey() {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());
        var data = new ByteArrayInputStream("x".getBytes());

        // when / then
        assertThatThrownBy(() -> sut.store("../escape.png", data, "image/png", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escape");
    }

    @Test
    void store_rejectsAnAbsoluteKey() {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());
        var data = new ByteArrayInputStream("x".getBytes());

        // when / then
        assertThatThrownBy(() -> sut.store("/etc/passwd", data, "text/plain", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_rejectsATraversalKey() {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());

        // when / then
        assertThatThrownBy(() -> sut.delete("../../victim.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void open_rejectsATraversalKey() {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());

        // when / then
        assertThatThrownBy(() -> sut.open("../escape.png")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escape");
    }

    @Test
    void open_wrapsIoExceptionAsUnchecked() throws IOException {

        // given - a file that exists but cannot be read
        var sut = new LocalMediaStorage(tempDir.toString());
        var unreadable = tempDir.resolve("unreadable.png");
        Files.write(unreadable, "x".getBytes());
        unreadable.toFile().setReadable(false);

        try {
            // when / then
            assertThatThrownBy(() -> sut.open("unreadable.png")).isInstanceOf(java.io.UncheckedIOException.class)
                    .hasMessageContaining("unreadable.png");
        } finally {
            unreadable.toFile().setReadable(true);
        }
    }

    @Test
    void length_wrapsIoExceptionAsUnchecked() throws IOException {

        // given - a file inside a directory that cannot be searched, which is not a missing file
        var sut = new LocalMediaStorage(tempDir.toString());
        var locked = tempDir.resolve("locked");
        Files.createDirectories(locked);
        Files.write(locked.resolve("file.png"), "x".getBytes());
        locked.toFile().setExecutable(false);

        try {
            // when / then
            assertThatThrownBy(() -> sut.length("locked/file.png")).isInstanceOf(java.io.UncheckedIOException.class)
                    .hasMessageContaining("locked/file.png");
        } finally {
            locked.toFile().setExecutable(true);
        }
    }

    @Test
    void length_rejectsATraversalKey() {

        // given
        var sut = new LocalMediaStorage(tempDir.toString());

        // when / then
        assertThatThrownBy(() -> sut.length("../escape.png")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escape");
    }
}
