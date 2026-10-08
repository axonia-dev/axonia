package dev.axonia.media.internal;

import java.io.InputStream;
import java.util.Optional;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobInputStreamOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import dev.axonia.IntegrationMetrics;
import dev.axonia.IntegrationMetrics.Integration;
import dev.axonia.media.MediaContent;
import dev.axonia.media.MediaStorage;
import lombok.RequiredArgsConstructor;

/**
 * Azure Blob Storage, reached with whatever identity the platform gives the process. Every call that leaves for the
 * store is counted and timed under the {@code azure-blob} integration tag.
 */
@RequiredArgsConstructor
class AzureBlobMediaStorage implements MediaStorage {

    /**
     * The SDK downloads the first block into memory before {@code openInputStream} returns, 4 MB by default. A quarter
     * of a megabyte covers an avatar, a badge and a reaction whole and bounds what a hero holds per request.
     */
    private static final int BLOCK_SIZE = 256 * 1024;

    private final BlobContainerClient container;
    private final IntegrationMetrics metrics;

    @Override
    public void store(String key, InputStream data, String contentType, long size) {

        var options = new BlobParallelUploadOptions(data).setHeaders(new BlobHttpHeaders().setContentType(contentType));
        metrics.run(Integration.AZURE_BLOB,
                () -> container.getBlobClient(key).uploadWithResponse(options, null, Context.NONE));
    }

    @Override
    public void delete(String key) {

        metrics.run(Integration.AZURE_BLOB, () -> {
            try {
                container.getBlobClient(key).delete();
            } catch (BlobStorageException refused) {
                rethrowUnlessAbsent(refused);
            }
        });
    }

    /**
     * A blob that is not there is the store answering, so it is caught inside the counted call and counts as a success,
     * as in the S3 adapter. Anything else the store refuses, a missing container or a denied identity included, is a
     * failure and is rethrown: it must never read as "no such image".
     */
    @Override
    public Optional<MediaContent> open(String key) {

        return metrics.call(Integration.AZURE_BLOB, () -> {
            try {
                var stream = container.getBlobClient(key)
                        .openInputStream(new BlobInputStreamOptions().setBlockSize(BLOCK_SIZE));
                return Optional.of(new MediaContent(stream, stream.getProperties().getBlobSize()));
            } catch (BlobStorageException refused) {
                return emptyWhenAbsent(refused);
            }
        });
    }

    @Override
    public Optional<Long> length(String key) {

        return metrics.call(Integration.AZURE_BLOB, () -> {
            try {
                return Optional.of(container.getBlobClient(key).getProperties().getBlobSize());
            } catch (BlobStorageException refused) {
                return emptyWhenAbsent(refused);
            }
        });
    }

    private static <T> Optional<T> emptyWhenAbsent(BlobStorageException refused) {

        rethrowUnlessAbsent(refused);
        return Optional.empty();
    }

    /** Only a missing blob is absence: a missing container is a misconfigured store, never a delete that succeeded. */
    private static void rethrowUnlessAbsent(BlobStorageException refused) {

        if (!BlobErrorCode.BLOB_NOT_FOUND.equals(refused.getErrorCode())) {
            throw refused;
        }
    }
}
