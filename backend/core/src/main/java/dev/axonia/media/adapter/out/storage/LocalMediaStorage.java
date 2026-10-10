package dev.axonia.media.adapter.out.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import dev.axonia.media.api.MediaContent;
import dev.axonia.media.api.MediaStorage;

public class LocalMediaStorage implements MediaStorage {

    private final Path basePath;

    public LocalMediaStorage(String basePath) {

        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream data, String contentType, long size) {

        try {
            var target = resolveWithinBase(key);
            var parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store media: " + key, e);
        }
    }

    @Override
    public void delete(String key) {

        try {
            Files.deleteIfExists(resolveWithinBase(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete media: " + key, e);
        }
    }

    @Override
    public Optional<MediaContent> open(String key) {

        try {
            var target = resolveWithinBase(key);
            var length = Files.size(target);
            return Optional.of(new MediaContent(Files.newInputStream(target), length));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open media: " + key, e);
        }
    }

    @Override
    public Optional<Long> length(String key) {

        try {
            return Optional.of(Files.size(resolveWithinBase(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read media: " + key, e);
        }
    }

    /** Keys are server-generated today; this guard keeps a future caller from ever escaping the media root. */
    private Path resolveWithinBase(String key) {

        var target = basePath.resolve(key).normalize();
        if (!target.startsWith(basePath)) {
            throw new IllegalArgumentException("Media key escapes the storage root: " + key);
        }
        return target;
    }
}
