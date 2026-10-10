package dev.axonia;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.ReactiveHealthContributors;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.spi.MailServer;
import dev.axonia.communication.application.MessageSubjectPolicies;
import dev.axonia.selfhosted.SelfHostedRequests;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;
import dev.axonia.selfhosted.SelfHostedTestInfrastructure;
import dev.axonia.selfhosted.configuration.SelfHostedProperties;
import dev.axonia.shared.api.ratelimit.RateLimitStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Self-hosted application as an installation starts it: its own database, its roles, a real issuer, the edge gate
 * on, no Redis. What each part proves on its own (sign-in, founding, the adapters' answers) is in that part's tests;
 * this one proves they are all in one context, and nothing of the Cloud's is.
 */
@SelfHostedSpringBootTest
class AxoniaSelfHostedApplicationTests {

    private static final List<String> SELF_HOSTED_MODULES = List.of("dev.axonia.selfhosted", "dev.axonia.installation");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private HealthContributorRegistry health;

    @Autowired
    private ObjectProvider<ReactiveHealthContributorRegistry> reactiveHealth;

    /** Every open port core's own tests stand in for, because only the Cloud's closed code answers it there. */
    static Stream<Class<?>> openPortsTheCloudAnswersInClosedCode() {

        return Arrays.stream(ClosedPortStandIns.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class)).<Class<?>>map(Method::getReturnType);
    }

    @ParameterizedTest
    @MethodSource("openPortsTheCloudAnswersInClosedCode")
    void eachOpenPortIsAnsweredOnceAndBySelfHostedCode(Class<?> port) {

        // when
        var actual = context.getBeansOfType(port).values();

        // then
        assertThat(actual).singleElement()
                .satisfies(adapter -> assertThat(SELF_HOSTED_MODULES).anyMatch(packageOf(adapter)::startsWith));
    }

    @Test
    void sendsOnlyTheOpenMessageTypes() {

        // when
        var actual = context.getBean(MessageSubjectPolicies.class).types();

        // then
        assertThat(actual).isEqualTo(MessageType.open());
    }

    @Test
    void readsTheInstallationsOwnSettings() {

        // when
        var actual = context.getBean(SelfHostedProperties.class);

        // then
        assertThat(actual.organization().id()).isEqualTo(SelfHostedTestInfrastructure.ORGANIZATION_ID);
    }

    @Test
    void keepsRateLimitsInMemoryAndReportsNoRedis() {

        // when
        var actual = AopUtils.getTargetClass(context.getBean(RateLimitStore.class));

        // then
        assertThat(actual.getSimpleName()).isEqualTo("InMemoryRateLimitStore");
        assertThat(everyContributor().getContributor("redis")).isNull();
    }

    /** No shipped file names a mail server, so Boot builds no sender and reports no mail contributor (I3). */
    @Test
    void everyDependencyIsUpAndNoneIsMail() {

        // when
        var actual = statuses("", everyContributor());

        // then
        assertThat(actual.keySet()).anyMatch(name -> "db".equals(name) || name.startsWith("db/"));
        assertThat(actual.keySet()).noneMatch(name -> name.startsWith("mail"));
        assertThat(actual).allSatisfy((name, status) -> assertThat(status).as(name).isEqualTo(Status.UP));
    }

    @Test
    void sendsMailThroughTheInstallationsOwnSettingsAndNotSpringBoots() {

        assertThat(context.getBeanNamesForType(JavaMailSender.class)).isEmpty();
        assertThat(AopUtils.getTargetClass(context.getBean(MailServer.class)).getName())
                .isEqualTo("dev.axonia.installation.adapter.out.communication.StoredMailServer");
    }

    @Test
    void isReadyForTheProbes() {

        // when
        var actual = mvc.get().uri("/actuator/health/readiness").exchange();

        // then
        assertThat(actual).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void refusesARequestThatDidNotComeThroughTheEdge() {

        // when
        var actual = mvc.get().uri("/axonia/me").exchange();

        // then
        assertThat(actual).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void asksARequestFromTheEdgeWithNoTokenToSignIn() {

        // when
        var actual = mvc.get().uri("/axonia/me").with(SelfHostedRequests.throughTheEdge()).exchange();

        // then
        assertThat(actual).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sendsNoTelemetry() {

        assertThat(context.getEnvironment().getProperty("axonia.telemetry.enabled", Boolean.class)).isFalse();
    }

    /**
     * The blocking contributors and the reactive ones, as the health endpoint reads them. Redis's contributor is a
     * reactive one whenever Reactor is on the classpath, which Lettuce brings, so the blocking registry alone would
     * never show it.
     */
    private HealthContributors everyContributor() {

        var reactive = reactiveHealth.stream().map(ReactiveHealthContributors::asHealthContributors);
        return HealthContributors.of(Stream.concat(Stream.of(health), reactive).toArray(HealthContributors[]::new));
    }

    private static String packageOf(Object bean) {

        return AopUtils.getTargetClass(bean).getPackageName();
    }

    private static Map<String, Status> statuses(String prefix, HealthContributors contributors) {

        var statuses = new TreeMap<String, Status>();
        contributors.stream().forEach(entry -> {
            var name = prefix + entry.name();
            switch (entry.contributor()) {
                case HealthIndicator indicator ->
                    statuses.put(name, Objects.requireNonNull(indicator.health()).getStatus());
                case CompositeHealthContributor composite -> statuses.putAll(statuses(name + "/", composite));
            }
        });
        return statuses;
    }
}
