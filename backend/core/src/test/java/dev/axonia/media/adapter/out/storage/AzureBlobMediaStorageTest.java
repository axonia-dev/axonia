package dev.axonia.media.adapter.out.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.common.StorageSharedKeyCredential;

import dev.axonia.media.api.MediaStorage;
import dev.axonia.media.api.MediaStorageContract;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against Azurite, with its published development account. Azurite accepts only a shared key, so what this proves is
 * the adapter's use of the Blob API; reaching a real account with the platform's identity is a manual check in the
 * Self-hosted runbook. One static container, never per-method.
 */
@Testcontainers
class AzureBlobMediaStorageTest extends MediaStorageContract {

    private static final String ACCOUNT = "devstoreaccount1";
    private static final String PUBLISHED_DEVELOPMENT_KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final String KEY = "reactions/acme/3f2b8c1e-5d4a-4b6c-9e8f-0a1b2c3d4e5f.webp";

    /** Tag 3.37.0, pinned by its digest. */
    @Container
    static final GenericContainer<?> AZURITE = new GenericContainer<>(
            "mcr.microsoft.com/azure-storage/azurite@sha256:830430c1da1a2d537e08f3e6764dd1f5ae00cf0346bcaf625b968ec3f0971fd5")
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck").withExposedPorts(10000);

    private static BlobServiceClient service;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final IntegrationMetrics metrics = new IntegrationMetrics(registry);

    @BeforeAll
    static void createTheContainer() {

        service = new BlobServiceClientBuilder()
                .endpoint("http://" + AZURITE.getHost() + ":" + AZURITE.getMappedPort(10000) + "/" + ACCOUNT)
                .credential(new StorageSharedKeyCredential(ACCOUNT, PUBLISHED_DEVELOPMENT_KEY))
                .httpClient(new JdkHttpClientBuilder().build()).buildClient();
        service.createBlobContainer("media");
    }

    @Override
    protected MediaStorage sut() {

        return new AzureBlobMediaStorage(service.getBlobContainerClient("media"), metrics);
    }

    @Test
    void theContentTypeIsStoredWithTheBlob() {

        // when
        sut().store(KEY, new ByteArrayInputStream(new byte[]{1, 2}), "image/webp", 2);

        // then
        var actual = service.getBlobContainerClient("media").getBlobClient(KEY).getProperties();
        assertThat(actual.getContentType()).isEqualTo("image/webp");
        assertThat(actual.getBlobSize()).isEqualTo(2L);
    }

    @Test
    void aBlobNobodyStoredIsAnAnsweredCallNotAFailure() {

        // when
        sut().open("reactions/acme/00000000-0000-0000-0000-000000000000.png");

        // then
        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
    }

    @Test
    void aStoreThatRefusesIsCountedAndRethrownNeverReportedAsAMissingImage() {

        // given
        var sut = new AzureBlobMediaStorage(service.getBlobContainerClient("no-such-container"), metrics);

        // when / then
        assertThatThrownBy(() -> sut.open(KEY)).isInstanceOf(BlobStorageException.class);
        assertThatThrownBy(() -> sut.store(KEY, new ByteArrayInputStream(new byte[]{1}), "image/png", 1))
                .isInstanceOf(BlobStorageException.class);
        assertThat(calls("failure")).isEqualTo(2.0);
    }

    @Test
    void everyCallThatLeavesForTheStoreIsCounted() throws IOException {

        // when
        sut().store(KEY, new ByteArrayInputStream(new byte[]{1}), "image/webp", 1);
        sut().length(KEY);
        sut().open(KEY).orElseThrow().stream().close();
        sut().delete(KEY);

        // then
        assertThat(calls("success")).isEqualTo(4.0);
    }

    /** A wrongly named container would otherwise turn a member's erasure into a silent, successful no-op. */
    @Test
    void aDeleteTheStoreRefusesIsAFailureNotANoOp() {

        // given
        var sut = new AzureBlobMediaStorage(service.getBlobContainerClient("no-such-container"), metrics);

        // when / then
        assertThatThrownBy(() -> sut.delete(KEY)).isInstanceOf(BlobStorageException.class);
        assertThat(calls("failure")).isEqualTo(1.0);
    }

    @Test
    void aLengthTheStoreRefusesToGiveIsAFailureNotAMissingImage() {

        // given
        var sut = new AzureBlobMediaStorage(service.getBlobContainerClient("no-such-container"), metrics);

        // when / then
        assertThatThrownBy(() -> sut.length(KEY)).isInstanceOf(BlobStorageException.class);
        assertThat(calls("failure")).isEqualTo(1.0);
    }

    private double calls(String outcome) {

        var counter = registry.find("axonia.integration.calls").tag("integration", "azure-blob").tag("outcome", outcome)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
