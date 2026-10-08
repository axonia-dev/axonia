package dev.axonia.media.internal;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;

import dev.axonia.IntegrationMetrics;
import dev.axonia.PublicUrlProperties;
import dev.axonia.media.AbsoluteMediaLinks;
import dev.axonia.media.MediaLinks;
import dev.axonia.media.MediaStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
class MediaConfig {

    private static final String LOCAL = "local";
    private static final String S3 = "s3";
    private static final String AZURE_BLOB = "azure-blob";
    private static final String AUTO = "auto";

    private final MediaStorageProperties properties;

    MediaConfig(MediaStorageProperties properties) {

        this.properties = properties;
    }

    /**
     * A wrong setting fails the boot rather than the first upload, and an unknown type, a stale {@code r2} included,
     * never falls back to local disk.
     */
    @Bean
    MediaStorage mediaStorage(IntegrationMetrics metrics) {

        var type = String.valueOf(properties.type());
        return switch (type) {
            case LOCAL -> new LocalMediaStorage(required(properties.local().basePath(), "app.media.local.base-path"));
            case S3 -> s3MediaStorage(properties.s3(), metrics);
            case AZURE_BLOB -> azureBlobMediaStorage(properties.azureBlob(), metrics);
            default -> throw new IllegalStateException(
                    "app.media.type must be " + LOCAL + ", " + S3 + " or " + AZURE_BLOB + ", but is " + type);
        };
    }

    /**
     * The path on the pages' own origin, so a page resolves it wherever it is served from. {@link PublicUrlProperties}
     * refuses a trailing slash, so the raw path is empty for an origin and {@code /axonia} for an application served
     * under one.
     */
    @Bean
    MediaLinks mediaLinks(PublicUrlProperties publicUrl) {

        return new MediaLinks(URI.create(publicUrl.publicBaseUrl()).getRawPath() + MediaLinks.PATH);
    }

    @Bean
    AbsoluteMediaLinks absoluteMediaLinks(PublicUrlProperties publicUrl) {

        return new AbsoluteMediaLinks(publicUrl.publicBaseUrl() + MediaLinks.PATH);
    }

    private static MediaStorage s3MediaStorage(MediaStorageProperties.S3 s3, IntegrationMetrics metrics) {

        var region = isSet(s3.region()) ? s3.region() : AUTO;
        var endpoint = AUTO.equals(region)
                ? required(s3.endpoint(), "app.media.s3.endpoint", "when app.media.s3.region is auto or left out")
                : s3.endpoint();
        var bucket = required(s3.bucket(), "app.media.s3.bucket");
        var builder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials(s3))
                .forcePathStyle(s3.pathStyle());
        if (isSet(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return new S3MediaStorage(builder.build(), bucket, metrics);
    }

    /**
     * A key pair when both halves are set; whatever identity the platform gives the process (EKS Pod Identity, a web
     * identity token, an instance profile) when both are left out. Half a pair is a mistake, never a fallback, and so
     * is a key set to an empty value: an emptied deployment variable must refuse the boot, not fall through to an
     * identity the platform may not have.
     */
    private static AwsCredentialsProvider credentials(MediaStorageProperties.S3 s3) {

        if (s3.accessKey() == null && s3.secretKey() == null) {
            return DefaultCredentialsProvider.builder().build();
        }
        if (!isSet(s3.accessKey()) || !isSet(s3.secretKey())) {
            throw new IllegalStateException("app.media.s3.access-key and app.media.s3.secret-key must be set together,"
                    + " or both left out to use the platform's identity; an empty value is neither");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
    }

    /**
     * One HTTP client is handed to both builders, so neither depends on which transport the SDK's service loader finds
     * first. Building the credential contacts nobody: a missing identity surfaces at the first call, as a counted
     * failure. A store that stops answering is given up on after three tries of thirty seconds each, not the SDK's
     * unbounded try.
     */
    private static MediaStorage azureBlobMediaStorage(MediaStorageProperties.AzureBlob azure,
            IntegrationMetrics metrics) {

        var endpoint = required(azure.endpoint(), "app.media.azure-blob.endpoint");
        var container = required(azure.container(), "app.media.azure-blob.container");
        if (!endpoint.startsWith("https://")) {
            throw new IllegalStateException("app.media.azure-blob.endpoint must be an https URL");
        }
        var http = new JdkHttpClientBuilder().build();
        var credential = new DefaultAzureCredentialBuilder().httpClient(http).build();
        var client = new BlobServiceClientBuilder().endpoint(endpoint).credential(credential).httpClient(http)
                .retryOptions(new RequestRetryOptions(RetryPolicyType.EXPONENTIAL, 3, Duration.ofSeconds(30), null,
                        null, null))
                .buildClient();
        return new AzureBlobMediaStorage(client.getBlobContainerClient(container), metrics);
    }

    private static String required(String value, String property) {

        return required(value, property, "");
    }

    private static String required(String value, String property, String condition) {

        if (!isSet(value)) {
            throw new IllegalStateException((property + " must be set " + condition).strip());
        }
        return value;
    }

    private static boolean isSet(String value) {

        return value != null && !value.isBlank();
    }
}
