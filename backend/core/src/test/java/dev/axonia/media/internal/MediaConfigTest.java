package dev.axonia.media.internal;

import java.net.URI;
import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RequestRetryPolicy;

import dev.axonia.IntegrationMetrics;
import dev.axonia.PublicUrlProperties;
import dev.axonia.media.AbsoluteMediaLinks;
import dev.axonia.media.MediaLinks;
import dev.axonia.media.MediaStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * A wrong media setting fails the boot, naming the property, instead of the first upload or a silent fall back to the
 * local disk.
 */
class MediaConfigTest {

    private static final String[] VALID_S3 = {"app.media.type=s3", "app.media.s3.endpoint=https://store.example.test",
            "app.media.s3.bucket=axonia"};

    private static final String[] WITH_KEYS = {"app.media.s3.access-key=access", "app.media.s3.secret-key=secret"};

    private static final String[] VALID_AZURE = {"app.media.type=azure-blob",
            "app.media.azure-blob.endpoint=https://axoniamedia.blob.core.windows.net",
            "app.media.azure-blob.container=axonia-media"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(IntegrationMetrics.class, () -> new IntegrationMetrics(new SimpleMeterRegistry()))
            .withBean(PublicUrlProperties.class, () -> new PublicUrlProperties("https://app.example.test"))
            .withUserConfiguration(MediaConfig.class);

    /** The core defaults, read the way the application reads them, are what a Self-hosted instance boots with. */
    @Test
    void theCoreDefaultsBuildTheLocalAdapterServedByTheApplication() {

        // given
        var configured = new ApplicationContextRunner()
                .withBean(IntegrationMetrics.class, () -> new IntegrationMetrics(new SimpleMeterRegistry()))
                .withUserConfiguration(BaseUrl.class, MediaConfig.class)
                .withInitializer(new ConfigDataApplicationContextInitializer());

        // when
        configured.run(context -> {
            // then
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MediaStorage.class)).isInstanceOf(LocalMediaStorage.class);
            assertThat(context.getBean(MediaLinks.class).urlFor("avatars/a/x.png"))
                    .isEqualTo("/public/media/avatars/a/x.png");
            assertThat(context.getBean(AbsoluteMediaLinks.class).urlFor("avatars/a/x.png"))
                    .isEqualTo("https://dev-local.example.com/public/media/avatars/a/x.png");
        });
    }

    @Test
    void s3BuildsTheObjectStoreAdapterAgainstTheConfiguredStore() {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS);

        // when
        configured.run(context -> {
            // then
            assertThat(context).hasNotFailed();
            var storage = context.getBean(MediaStorage.class);
            assertThat(storage).isInstanceOf(S3MediaStorage.class).extracting("bucket").isEqualTo("axonia");
            assertThat(context.getBean(MediaLinks.class).urlFor("logo.png")).isEqualTo("/public/media/logo.png");
            assertThat(context.getBean(AbsoluteMediaLinks.class).urlFor("logo.png"))
                    .isEqualTo("https://app.example.test/public/media/logo.png");
        });
    }

    @Test
    void anApplicationServedUnderAPathKeepsThatPathInEveryImageAddress() {

        // given
        var configured = new ApplicationContextRunner()
                .withBean(IntegrationMetrics.class, () -> new IntegrationMetrics(new SimpleMeterRegistry()))
                .withBean(PublicUrlProperties.class,
                        () -> new PublicUrlProperties("https://intranet.example.test/axonia"))
                .withUserConfiguration(MediaConfig.class).withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS);

        // when / then
        configured.run(context -> {
            assertThat(context.getBean(MediaLinks.class).urlFor("logo.png")).isEqualTo("/axonia/public/media/logo.png");
            assertThat(context.getBean(AbsoluteMediaLinks.class).urlFor("logo.png"))
                    .isEqualTo("https://intranet.example.test/axonia/public/media/logo.png");
        });
    }

    @Test
    void azureBlobBuildsTheBlobAdapterWithNoKeyConfigured() {

        // given
        var configured = runner.withPropertyValues(VALID_AZURE);

        // when / then
        configured.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MediaStorage.class)).isInstanceOf(AzureBlobMediaStorage.class)
                    .extracting("container", type(BlobContainerClient.class)).satisfies(container -> {
                        assertThat(container.getBlobContainerName()).isEqualTo("axonia-media");
                        assertThat(container.getAccountUrl()).isEqualTo("https://axoniamedia.blob.core.windows.net");
                    });
        });
    }

    @ParameterizedTest
    @CsvSource({"app.media.azure-blob.endpoint=,app.media.azure-blob.endpoint must be set",
            "app.media.azure-blob.container=,app.media.azure-blob.container must be set",
            "app.media.azure-blob.endpoint=http://axoniamedia.blob.core.windows.net,app.media.azure-blob.endpoint must be an https URL"})
    void refusesAnAzureSettingThatCannotWork(String setting, String message) {

        // given
        var configured = runner.withPropertyValues(VALID_AZURE).withPropertyValues(setting);

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining(message));
    }

    @ParameterizedTest
    @CsvSource({"app.media.azure-blob.endpoint=${AZURE_BLOB_ENDPOINT_NOBODY_SET},app.media.azureBlob.endpoint",
            "app.media.azure-blob.container=${AZURE_BLOB_CONTAINER_NOBODY_SET},app.media.azureBlob.container"})
    void refusesAnAzureSettingLeftAsAnUnresolvedPlaceholder(String setting, String field) {

        // given
        var configured = runner.withPropertyValues(VALID_AZURE).withPropertyValues(setting);

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"r2", "gcs", "S3"})
    void refusesAnUnknownType(String type) {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS)
                .withPropertyValues("app.media.type=" + type);

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("app.media.type must be local, s3 or azure-blob, but is " + type));
    }

    @Test
    void refusesAMissingType() {

        // given
        var configured = runner.withPropertyValues("app.media.local.base-path=media");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("app.media.type must be local, s3 or azure-blob, but is null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "bucket"})
    void refusesS3WithoutWhereTheStoreIs(String setting) {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS)
                .withPropertyValues("app.media.s3." + setting + "=");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("app.media.s3." + setting + " must be set"));
    }

    @Test
    void refusesS3WithNoStoreSettingsAtAll() {

        // given
        var configured = runner.withPropertyValues("app.media.type=s3");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("app.media.s3.endpoint must be set"));
    }

    @Test
    void refusesLocalWithoutABasePath() {

        // given
        var configured = runner.withPropertyValues("app.media.type=local");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("app.media.local.base-path must be set"));
    }

    /**
     * The binder hands an unset deployment variable over as its literal placeholder text, which the AWS SDK would
     * accept as a credential until the first upload.
     */
    @ParameterizedTest
    @CsvSource({"access-key, accessKey", "secret-key, secretKey"})
    void refusesAnUnresolvedPlaceholderAsACredential(String property, String field) {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS)
                .withPropertyValues("app.media.s3." + property + "=${MEDIA_S3_UNSET}");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("app.media.s3." + field)
                .hasMessageContaining("must be a resolved value, not an unresolved property placeholder"));
    }

    @Test
    void aKeyPairIsUsedWhenBothHalvesAreSet() {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS);

        // when / then
        configured.run(context -> assertThat(s3ClientOf(context).serviceClientConfiguration().credentialsProvider())
                .isInstanceOf(StaticCredentialsProvider.class));
    }

    @Test
    void thePlatformsIdentityIsUsedWhenNoKeyIsSet() {

        // given
        var configured = runner.withPropertyValues(VALID_S3);

        // when / then
        configured.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(s3ClientOf(context).serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(DefaultCredentialsProvider.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.media.s3.access-key=access", "app.media.s3.secret-key=secret"})
    void refusesHalfAKeyPairNamingBothSettings(String half) {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(half);

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("app.media.s3.access-key").hasStackTraceContaining("app.media.s3.secret-key")
                .hasStackTraceContaining("must be set together"));
    }

    /** An emptied deployment variable is a mistake, not a request for the platform's identity. */
    @ParameterizedTest
    @CsvSource({"'app.media.s3.access-key=', 'app.media.s3.secret-key= '",
            "'app.media.s3.access-key=', 'app.media.s3.secret-key=secret'",
            "'app.media.s3.access-key=access', 'app.media.s3.secret-key='"})
    void refusesAKeySetToAnEmptyValue(String accessKey, String secretKey) {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(accessKey, secretKey);

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("must be set together"));
    }

    @Test
    void theRegionDefaultsToAutoForAStoreThatHasNone() {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues(WITH_KEYS);

        // when / then
        configured.run(context -> assertThat(s3ClientOf(context).serviceClientConfiguration().region())
                .isEqualTo(Region.of("auto")));
    }

    @Test
    void aNamedRegionNeedsNoEndpoint() {

        // given
        var configured = runner.withPropertyValues("app.media.type=s3", "app.media.s3.bucket=axonia",
                "app.media.s3.region=eu-central-1");

        // when / then
        configured.run(context -> {
            assertThat(context).hasNotFailed();
            var actual = s3ClientOf(context).serviceClientConfiguration();
            assertThat(actual.region()).isEqualTo(Region.EU_CENTRAL_1);
            assertThat(actual.endpointOverride()).isEmpty();
        });
    }

    @Test
    void aNamedRegionMayStillNameAnEndpoint() {

        // given
        var configured = runner.withPropertyValues(VALID_S3).withPropertyValues("app.media.s3.region=us-east-1");

        // when / then
        configured.run(context -> assertThat(s3ClientOf(context).serviceClientConfiguration().endpointOverride())
                .contains(URI.create("https://store.example.test")));
    }

    @Test
    void theAutoRegionStillRequiresAnEndpoint() {

        // given
        var configured = runner.withPropertyValues("app.media.type=s3", "app.media.s3.bucket=axonia");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining(
                "app.media.s3.endpoint must be set when app.media.s3.region is auto or left out"));
    }

    /**
     * IRSA on EKS exchanges a service account token through STS; the default chain skips that step when STS is absent.
     */
    @Test
    void theWebIdentityCredentialsProviderIsOnTheClasspath() {

        // when / then
        assertThatCode(() -> Class
                .forName("software.amazon.awssdk.services.sts.auth.StsWebIdentityTokenFileCredentialsProvider"))
                .doesNotThrowAnyException();
    }

    private static S3Client s3ClientOf(AssertableApplicationContext context) {

        return (S3Client) ReflectionTestUtils.getField(context.getBean(MediaStorage.class), "s3Client");
    }

    /** A setting that says where requests go is refused as a placeholder too, or the first call would find out. */
    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "bucket", "region"})
    void refusesAnUnresolvedPlaceholderForWhereTheStoreIs(String setting) {

        // given
        var configured = runner.withPropertyValues(VALID_S3)
                .withPropertyValues("app.media.s3." + setting + "=${MEDIA_S3_UNSET}");

        // when / then
        configured.run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("app.media.s3." + setting)
                .hasMessageContaining("must be a resolved value, not an unresolved property placeholder"));
    }

    /** A store that stops answering must not hold a request thread for minutes: three tries of thirty seconds. */
    @Test
    void theAzureClientGivesUpOnAStoreThatStopsAnswering() {

        // given
        var configured = runner.withPropertyValues(VALID_AZURE);

        // when / then
        configured.run(context -> {
            var container = (BlobContainerClient) ReflectionTestUtils.getField(context.getBean(MediaStorage.class),
                    "container");
            var pipeline = container.getHttpPipeline();
            var retry = IntStream.range(0, pipeline.getPolicyCount()).mapToObj(pipeline::getPolicy)
                    .filter(RequestRetryPolicy.class::isInstance).findFirst().orElseThrow();
            assertThat((RequestRetryOptions) ReflectionTestUtils.getField(retry, "requestRetryOptions"))
                    .satisfies(options -> {
                        assertThat(options.getMaxTries()).isEqualTo(3);
                        assertThat(options.getTryTimeoutDuration()).isEqualTo(Duration.ofSeconds(30));
                    });
        });
    }

    /** Binds {@code axonia.public-base-url} from the real files, as the application's own configuration does. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PublicUrlProperties.class)
    static class BaseUrl {
    }
}
