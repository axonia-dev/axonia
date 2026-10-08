package dev.axonia.media.internal;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.IntegrationMetrics;
import dev.axonia.PublicUrlProperties;
import dev.axonia.media.MediaStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The S3 adapter as the configuration builds it, against a real S3-compatible store that checks every request's
 * signature: a named region, a key pair and path-style addressing. Nothing else in the suite sends an S3 request
 * anywhere. One static container.
 */
@Testcontainers
class S3CompatibleStoreTest extends MediaStorageContract {

    private static final String ACCESS = "axonia-test";
    private static final String SECRET = "axonia-test-secret";
    private static final String BUCKET = "media";

    /** The store's only identity: the test's key pair, with every action on the one bucket. */
    private static final String IDENTITIES = """
            {"identities": [{"name": "axonia", "credentials": [{"accessKey": "%s", "secretKey": "%s"}],
              "actions": ["Admin:%s"]}]}
            """.formatted(ACCESS, SECRET, BUCKET);

    /** Tag 4.48, pinned by its digest. Ready once it refuses an unsigned request. */
    @Container
    static final GenericContainer<?> STORE = new GenericContainer<>(
            "chrislusf/seaweedfs@sha256:4e61d15fd35994cb1e43e1e553dff106794841fd9a99ade2fc8c8bfce4d7872d")
            .withCopyToContainer(Transferable.of(IDENTITIES), "/etc/seaweedfs/s3.json")
            .withCommand("server", "-dir=/data", "-s3", "-s3.config=/etc/seaweedfs/s3.json").withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403).withStartupTimeout(Duration.ofMinutes(2)));

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void createTheBucketAndBuildTheAdapterFromConfiguration() {

        try (var admin = S3Client.builder().endpointOverride(URI.create(endpoint())).region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS, SECRET)))
                .build()) {
            admin.createBucket(request -> request.bucket(BUCKET));
        }
        context = adapterSigningWith(SECRET);
    }

    @AfterAll
    static void close() {

        context.close();
    }

    @Override
    MediaStorage sut() {

        return context.getBean(MediaStorage.class);
    }

    @Test
    void aRequestSignedWithTheWrongSecretIsRefused() {

        // given
        var bytes = new byte[]{1, 2, 3};
        try (var wrong = adapterSigningWith("not-the-secret")) {
            var storage = wrong.getBean(MediaStorage.class);

            // when / then
            assertThatThrownBy(() -> storage.store("avatars/acme/5b0c3a1e-2d4f-4e6a-8b9c-0d1e2f3a4b5c.png",
                    new ByteArrayInputStream(bytes), "image/png", bytes.length))
                    .isInstanceOfSatisfying(S3Exception.class, refused -> {
                        assertThat(refused.statusCode()).isEqualTo(403);
                        assertThat(refused.awsErrorDetails().errorCode()).isEqualTo("SignatureDoesNotMatch");
                    });
        }
    }

    private static String endpoint() {

        return "http://" + STORE.getHost() + ":" + STORE.getMappedPort(8333);
    }

    private static ConfigurableApplicationContext adapterSigningWith(String secret) {

        var application = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("app.media.type=s3", "app.media.s3.endpoint=" + endpoint(),
                "app.media.s3.bucket=" + BUCKET, "app.media.s3.region=us-east-1", "app.media.s3.path-style=true",
                "app.media.s3.access-key=" + ACCESS, "app.media.s3.secret-key=" + secret).applyTo(application);
        application.registerBean(IntegrationMetrics.class, () -> new IntegrationMetrics(new SimpleMeterRegistry()));
        application.registerBean(PublicUrlProperties.class, () -> new PublicUrlProperties("https://app.example.test"));
        application.register(MediaConfig.class);
        application.refresh();
        return application;
    }
}
