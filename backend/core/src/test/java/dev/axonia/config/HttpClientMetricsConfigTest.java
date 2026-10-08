package dev.axonia.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The tag values are hosts a customer can choose, so the assertions are about what the meter registry ends up holding
 * rather than about the filter in isolation.
 */
class HttpClientMetricsConfigTest {

    private static final String METER = "http.client.requests";

    private final ApplicationContextRunner runner = runnerContributing(new OutboundHost("https://login.axonia.example"),
            new OutboundHost("https://payments.example"));

    /**
     * The four suffix shapes {@code TeamsPlatform} claims, one host each. Without the filter these are four series;
     * with a real customer base they are one per configured webhook, and nothing in Boot bounds them.
     */
    @ParameterizedTest
    @ValueSource(strings = {"acme.webhook.office.com", "prod-17.logic.azure.com",
            "orgabc.1f.environment.api.powerplatform.com", "somewhere.else.test"})
    void foldsATenantConfiguredHostIntoOneSeries(String host) {

        runner.run(context -> assertThat(clientNameRecordedFor(context.getBean(MeterRegistry.class), host))
                .isEqualTo(HttpClientMetricsConfig.OTHER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"graph.microsoft.com", "login.microsoftonline.com", "login.axonia.example",
            "payments.example", "hooks.slack.com"})
    void keepsAHostThisRepositoryNames(String host) {

        runner.run(context -> assertThat(clientNameRecordedFor(context.getBean(MeterRegistry.class), host))
                .isEqualTo(host));
    }

    @Test
    void boundedOutboundClientName_keepsAContributedHostAndIgnoresAMalformedOne() {

        // given
        var registry = new SimpleMeterRegistry();

        // when
        registry.config()
                .meterFilter(new HttpClientMetricsConfig().boundedOutboundClientName(new ObservationProperties(),
                        List.of(new OutboundHost("https://kc.example"), new OutboundHost("not a url"))));

        // then
        assertThat(clientNameRecordedFor(registry, "kc.example")).isEqualTo("kc.example");
        assertThat(clientNameRecordedFor(registry, "somewhere.else.test")).isEqualTo(HttpClientMetricsConfig.OTHER);
    }

    /**
     * The identity provider's and a payment provider's hosts move per deployment, so they are contributed by the
     * modules that call them rather than pinned as a literal: a Self-hosted operator's own identity provider - not
     * Cloud's - must keep its own tag too, exactly as the first host does above.
     */
    @Test
    void keepsAContributedHostThatIsNotTheCloudOne() {

        runnerContributing(new OutboundHost("https://idp.customer.example")).run(context -> assertThat(
                clientNameRecordedFor(context.getBean(MeterRegistry.class), "idp.customer.example"))
                .isEqualTo("idp.customer.example"));
    }

    /**
     * A build with no module that calls the identity provider or a payment provider contributes no host at all, and
     * must still start and keep the fixed ones.
     */
    @Test
    void startsUpWhenNoModuleContributesAHost() {

        runnerContributing().run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(MeterRegistry.class);
            assertThat(clientNameRecordedFor(registry, "hooks.slack.com")).isEqualTo("hooks.slack.com");
            assertThat(clientNameRecordedFor(registry, "login.axonia.example"))
                    .isEqualTo(HttpClientMetricsConfig.OTHER);
        });
    }

    /**
     * A blank base URL - unset rather than misconfigured, in practice - must not fail every profile's startup over a
     * metrics tag. {@code URI.create("")} answers a {@code null} host without throwing, and
     * {@link HttpClientMetricsConfig}'s {@code knownHosts} must drop that {@code null} rather than let it reach a
     * collector that throws {@code NullPointerException} on one.
     */
    @Test
    void startsUpWhenAContributedHostIsBlank() {

        runnerContributing(new OutboundHost(""), new OutboundHost("https://payments.example")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(clientNameRecordedFor(context.getBean(MeterRegistry.class), "somewhere.else.test"))
                    .isEqualTo(HttpClientMetricsConfig.OTHER);
        });
    }

    /**
     * A scheme-less value - a bare {@code host:port}, the shape a developer most often mistypes - parses without
     * throwing but with a {@code null} host: {@code URI.create} reads the part before the colon as the scheme, not an
     * authority.
     */
    @Test
    void startsUpWhenAContributedHostHasNoScheme() {

        runnerContributing(new OutboundHost("localhost:8081"), new OutboundHost("https://payments.example"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(clientNameRecordedFor(context.getBean(MeterRegistry.class), "somewhere.else.test"))
                            .isEqualTo(HttpClientMetricsConfig.OTHER);
                });
    }

    /**
     * Two contributions resolving to the same host is a duplicate value, not a startup failure: {@code Set.of} throws
     * {@code IllegalArgumentException} on a duplicate element, which {@code Collectors.toUnmodifiableSet()} tolerates.
     */
    @Test
    void startsUpWhenTheContributedHostsAreTheSame() {

        runnerContributing(new OutboundHost("https://shared.example.com"),
                new OutboundHost("https://shared.example.com")).run(context -> {
                    assertThat(context).hasNotFailed();
                    var registry = context.getBean(MeterRegistry.class);
                    assertThat(clientNameRecordedFor(registry, "shared.example.com")).isEqualTo("shared.example.com");
                    assertThat(clientNameRecordedFor(registry, "somewhere.else.test"))
                            .isEqualTo(HttpClientMetricsConfig.OTHER);
                });
    }

    /**
     * The assertion whose absence hid a defect: {@code Meter.Id.replaceTags} answers an id with ONLY the tags handed to
     * it, so a fold written that way silently took {@code status}, {@code outcome}, {@code method}, {@code exception}
     * and {@code uri} off every series it touched - and an alert on any of them would then never match a call to a
     * tenant-configured host, on the one meter this filter exists to keep usable. The six keys below are what
     * {@code DefaultClientRequestObservationConvention.getLowCardinalityKeyValues} emits.
     */
    @Test
    void keepsEveryOtherTagOnAFoldedSeries() {

        runner.run(context -> {
            var registry = context.getBean(MeterRegistry.class);
            Timer.builder(METER).tag(HttpClientMetricsConfig.CLIENT_NAME, "acme.webhook.office.com")
                    .tag("exception", "none").tag("method", "POST").tag("outcome", "SERVER_ERROR").tag("status", "500")
                    .tag("uri", "none").register(registry);

            var id = registry.get(METER).tag("outcome", "SERVER_ERROR").timer().getId();

            assertThat(id.getTag(HttpClientMetricsConfig.CLIENT_NAME)).isEqualTo(HttpClientMetricsConfig.OTHER);
            assertThat(id.getTags()).extracting(Tag::getKey, Tag::getValue).containsExactlyInAnyOrder(
                    tuple(HttpClientMetricsConfig.CLIENT_NAME, HttpClientMetricsConfig.OTHER),
                    tuple("exception", "none"), tuple("method", "POST"), tuple("outcome", "SERVER_ERROR"),
                    tuple("status", "500"), tuple("uri", "none"));
        });
    }

    /**
     * Scoped to the one meter that carries the tag. A blanket rewrite of {@code client.name} would also reach any
     * future meter that borrows the key, which is a decision nobody would have taken.
     */
    @Test
    void leavesAnotherMeterCarryingTheSameTagAlone() {

        runner.run(context -> {
            var registry = context.getBean(MeterRegistry.class);
            Timer.builder("some.other.meter").tag(HttpClientMetricsConfig.CLIENT_NAME, "acme.webhook.office.com")
                    .register(registry);
            assertThat(registry.get("some.other.meter").timer().getId().getTag(HttpClientMetricsConfig.CLIENT_NAME))
                    .isEqualTo("acme.webhook.office.com");
        });
    }

    private static ApplicationContextRunner runnerContributing(OutboundHost... hosts) {

        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                        SimpleMetricsExportAutoConfiguration.class, ObservationAutoConfiguration.class))
                .withUserConfiguration(HttpClientMetricsConfig.class);
        for (var index = 0; index < hosts.length; index++) {
            var host = hosts[index];
            runner = runner.withBean("outboundHost" + index, OutboundHost.class, () -> host);
        }
        return runner;
    }

    private static String clientNameRecordedFor(MeterRegistry registry, String host) {

        return Timer.builder(METER).tag(HttpClientMetricsConfig.CLIENT_NAME, host).register(registry).getId()
                .getTag(HttpClientMetricsConfig.CLIENT_NAME);
    }
}
