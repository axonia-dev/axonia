package dev.axonia.media.adapter.out.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.media.api.MediaContent;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class S3MediaStorageTest {

    @Mock
    private S3Client s3Client;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final IntegrationMetrics metrics = new IntegrationMetrics(registry);

    private S3MediaStorage sut() {

        return new S3MediaStorage(s3Client, "axonia", metrics);
    }

    @Test
    void store_putsTheObject() {

        // given
        var content = "image-bytes".getBytes();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        sut().store("communities/1/badge/abc.png", new ByteArrayInputStream(content), "image/png", content.length);

        // then
        var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("axonia");
        assertThat(captor.getValue().key()).isEqualTo("communities/1/badge/abc.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(captor.getValue().contentLength()).isEqualTo(content.length);
    }

    @Test
    void delete_removesObject() {

        // given
        var captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        when(s3Client.deleteObject(captor.capture())).thenReturn(null);

        // when
        sut().delete("communities/1/badge/abc.png");

        // then
        assertThat(captor.getValue().bucket()).isEqualTo("axonia");
        assertThat(captor.getValue().key()).isEqualTo("communities/1/badge/abc.png");
        assertThat(calls("success")).isEqualTo(1.0);
    }

    @Test
    void open_returnsTheObjectBodyForAnExistingKey() throws IOException {

        // given
        var content = "image-bytes".getBytes();
        var response = new ResponseInputStream<>(GetObjectResponse.builder().contentLength(3L).build(),
                new ByteArrayInputStream(content));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        // when
        var opened = sut().open("communities/1/badge/abc.png");

        // then
        assertThat(opened).isPresent();
        assertThat(opened.get().stream().readAllBytes()).isEqualTo(content);
        assertThat(opened.orElseThrow().length()).isEqualTo(3L);

        var captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("axonia");
        assertThat(captor.getValue().key()).isEqualTo("communities/1/badge/abc.png");
    }

    @Test
    void open_returnsEmptyWhenTheObjectIsAbsent() {

        // given
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        // when / then
        assertThat(sut().open("communities/1/badge/missing.png")).isEmpty();
    }

    @Test
    void store_countsTheCallToTheObjectStore() {

        // given
        var content = "image-bytes".getBytes();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        sut().store("communities/1/badge/abc.png", new ByteArrayInputStream(content), "image/png", content.length);

        // then
        assertThat(calls("success")).isEqualTo(1.0);
    }

    @Test
    void store_countsARefusedCallAndRethrowsIt() {

        // given
        var content = "image-bytes".getBytes();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        // when / then
        assertThatThrownBy(() -> sut().store("communities/1/badge/abc.png", new ByteArrayInputStream(content),
                "image/png", content.length)).isInstanceOf(S3Exception.class);
        assertThat(calls("failure")).isEqualTo(1.0);
    }

    @Test
    void delete_countsARefusedCallAndRethrowsIt() {

        // given
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        // when / then
        assertThatThrownBy(() -> sut().delete("communities/1/badge/abc.png")).isInstanceOf(S3Exception.class);
        assertThat(calls("failure")).isEqualTo(1.0);
    }

    /**
     * An avatar nobody uploaded is not the object store refusing: the store answered, and the object simply is not
     * there. Counted as a success deliberately, which is the one place this adapter knows something the HTTP
     * interceptor cannot - see {@code S3MediaStorage#open}.
     */
    @Test
    void open_countsAnAbsentObjectAsAnAnsweredCall() {

        // given
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        // when
        sut().open("communities/1/badge/missing.png");

        // then
        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
    }

    @Test
    void open_countsARefusedCallAndRethrowsIt() {

        // given
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        // when / then
        assertThatThrownBy(() -> sut().open("communities/1/badge/abc.png")).isInstanceOf(S3Exception.class);
        assertThat(calls("failure")).isEqualTo(1.0);
    }

    @Test
    void open_reportsAnUnknownLengthWhenTheStoreSendsNone() {

        // given
        var response = new ResponseInputStream<>(GetObjectResponse.builder().build(),
                new ByteArrayInputStream(new byte[]{1}));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        // when
        var actual = sut().open("avatars/acme/a.png");

        // then
        assertThat(actual).get().extracting(MediaContent::length).isEqualTo(-1L);
    }

    @Test
    void length_asksForTheObjectsHeadAndNeverItsBody() {

        // given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(42L).build());

        // when
        var actual = sut().length("avatars/acme/a.png");

        // then
        assertThat(actual).contains(42L);
        var captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("axonia");
        assertThat(captor.getValue().key()).isEqualTo("avatars/acme/a.png");
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void length_reportsAnUnknownLengthWhenTheStoreSendsNone() {

        // given
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        // when
        var actual = sut().length("avatars/acme/a.png");

        // then
        assertThat(actual).contains(-1L);
        assertThat(calls("success")).isEqualTo(1.0);
    }

    @Test
    void length_countsAnAbsentObjectAsAnAnsweredCall() {

        // given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        // when
        var actual = sut().length("avatars/acme/missing.png");

        // then
        assertThat(actual).isEmpty();
        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
    }

    @Test
    void length_countsARefusedCallAndRethrowsIt() {

        // given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("access denied").build());

        // when / then
        assertThatThrownBy(() -> sut().length("avatars/acme/a.png")).isInstanceOf(S3Exception.class);
        assertThat(calls("failure")).isEqualTo(1.0);
    }

    private double calls(String outcome) {

        return registry.get("axonia.integration.calls").tag("integration", "s3").tag("outcome", outcome).counter()
                .count();
    }
}
