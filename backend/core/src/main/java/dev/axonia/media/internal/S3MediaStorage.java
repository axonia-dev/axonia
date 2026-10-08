package dev.axonia.media.internal;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

import dev.axonia.IntegrationMetrics;
import dev.axonia.IntegrationMetrics.Integration;
import dev.axonia.media.MediaContent;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Speaks the S3 API to any compatible object store.
 *
 * <p>
 * Every call that leaves this process for the store is counted and timed through {@link IntegrationMetrics}, under the
 * {@code s3} integration tag, whatever the store is.
 */
@RequiredArgsConstructor
class S3MediaStorage implements MediaStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final IntegrationMetrics metrics;

    @Override
    public void store(String key, InputStream data, String contentType, long size) {

        var request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(size)
                .build();
        metrics.run(Integration.S3, () -> s3Client.putObject(request, RequestBody.fromInputStream(data, size)));
    }

    @Override
    public void delete(String key) {

        metrics.run(Integration.S3,
                () -> s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()));
    }

    /**
     * The missing-key case is caught inside the counted call, not around it, so it counts as a success: the store
     * answered, and the object simply is not there - an avatar nobody uploaded is not the store refusing. The HTTP
     * interceptor counts a 404 as a failure because it cannot know that; here the adapter does.
     */
    @Override
    public Optional<MediaContent> open(String key) {

        var request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return metrics.call(Integration.S3, () -> {
            try {
                var object = s3Client.getObject(request);
                var length = object.response().contentLength();
                return Optional.of(new MediaContent(object, length == null ? -1 : length));
            } catch (NoSuchKeyException absent) {
                return Optional.empty();
            }
        });
    }

    /**
     * An absent object counts as an answered call, for the reason {@link #open(String)} gives. A store that sends no
     * length reports an unknown one, as {@link #open(String)} does.
     */
    @Override
    public Optional<Long> length(String key) {

        var request = HeadObjectRequest.builder().bucket(bucket).key(key).build();
        return metrics.call(Integration.S3, () -> {
            try {
                return Optional.of(Objects.requireNonNullElse(s3Client.headObject(request).contentLength(), -1L));
            } catch (NoSuchKeyException absent) {
                return Optional.empty();
            }
        });
    }
}
