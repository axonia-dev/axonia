package dev.axonia;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.config.AttributeAllowlist;
import dev.axonia.organization.OrganizationKind;
import dev.axonia.organization.OrganizationRegistration;
import dev.axonia.organization.OrganizationService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The gate the personal-data footprint cannot provide. {@code PersonalDataFootprintTest} guards tables; a span is not a
 * table, so nothing else in this build can see an email address leaving the process inside telemetry.
 *
 * <p>
 * The sentinels are deliberately absurd rather than realistic. A test asserting that "no attribute looks like an email"
 * can only catch what its regex predicts; asserting that these exact strings never appear catches a display name, a
 * tag, a slug or a free-text body just as well. The assertion is over {@code SpanData.toString()}, which renders
 * {@code attributes=}, {@code events=}, {@code status=} and {@code resource=} alike, so it reaches every carrier on the
 * span rather than only the one the allowlist governs.
 *
 * <p>
 * Real HTTP on a real port, like {@code RequestTelemetryInstalledTest}, and for the same reason: the server span and
 * the tenant stamped onto it both come from the servlet chain, which a standalone {@code MockMvc} does not run.
 *
 * <p>
 * The exporter is registered as an ordinary {@code SpanExporter} bean, so Boot's own
 * {@code OpenTelemetryTracingAutoConfiguration} wraps it in the same {@code CompositeSpanExporter} it wraps the OTLP
 * one in, behind the same {@code SpanFilter} beans. That is what makes this an end-to-end assertion about the
 * production path rather than about a hand-built pipeline, and it is also the ordering proof: the composite maps every
 * span through its filters and only then calls {@code export}, so an attribute this exporter never receives is one that
 * was removed before any exporter could have shipped it.
 *
 * <p>
 * Sampling is forced to 1.0. Boot samples at 0.1 by default, which would make a build-failing guard pass nine times in
 * ten on a leak, and flakiness is the worst property a guard can have.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.tracing.sampling.probability=1.0")
@Import(TelemetryPersonalDataGuardTest.GuardProbeConfiguration.class)
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class TelemetryPersonalDataGuardTest {

    private static final String SENTINEL_EMAIL = "zzsentinel@example.invalid";

    private static final String SENTINEL_NAME = "Zzsentinel Qqunlikely";

    private static final String ORGANIZATION = "telemetry-guard-acme";

    private static final String USER = "3b7e2c41-5a19-4f0d-8c62-7e4a1b9d3f08";

    private static final AttributeKey<String> ROUTE = AttributeKey.stringKey("uri");

    /**
     * One per driven request, and the wait below blocks until all four have been exported. That makes coverage an
     * assertion rather than an assumption: a request that stopped reaching a handler, or a span that had not finished
     * when the assertions ran, is a timeout rather than a quiet pass.
     */
    private static final Set<String> EXPECTED_ROUTES = Set.of("/axonia/communities", "/axonia/expertise/me/dismiss",
            "/axonia/communities/{slug}", "/axonia/telemetry-guard-probe/{handle}");

    /** What Spring Framework's DefaultServerRequestObservationConvention puts on every server span. */
    private static final Set<String> HTTP_QUINTET = Set.of("method", "uri", "status", "outcome", "exception");

    private static final AtomicBoolean SEEDED = new AtomicBoolean();

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private OrganizationService organizations;

    @Autowired
    private InMemorySpanExporter exporter;

    @Autowired
    private SdkTracerProvider tracerProvider;

    /**
     * Stands in for the resource-server decoder so a forged bearer authenticates without reaching the identity
     * provider.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * The token carries both sentinels, because a token claim is the likeliest way either would reach a span: it is
     * what {@code RequestTelemetryFilter} reads, and stamping the wrong claim off it is a one-word mistake.
     *
     * <p>
     * The Organization has to exist and be ACTIVE, because {@code OrganizationLifecycleFilter} answers 401 for one that
     * does not, and every probe below would then be refused before reaching a handler.
     */
    @BeforeEach
    void seedTheTenantStubTheTokenAndResetTheExporter() {

        if (SEEDED.compareAndSet(false, true)) {
            OrganizationScope.runAs(ORGANIZATION, () -> organizations.register(OrganizationRegistration.builder()
                    .id(ORGANIZATION).name("Telemetry Guard Acme").kind(OrganizationKind.ENTERPRISE).build()));
        }
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("token").header("alg", "none").subject(USER)
                .claim("org", ORGANIZATION).claim("name", SENTINEL_NAME).claim("email", SENTINEL_EMAIL).build());
        exporter.reset();
    }

    /**
     * The two preconditions come first, and they are not decoration. Everything this test says about events and about
     * the status description is only said if the probe's exception actually escaped the dispatcher, and nothing else
     * here would notice if it stopped. {@code EXPECTED_ROUTES} proves the {@code uri} attribute arrived, and the
     * probe's own {@code isEqualTo(500)} is satisfied just as well by an exception a handler mapped to 500 - which is
     * what an {@code ExceptionHandler(Exception.class)} on {@code GlobalExceptionHandler} would turn it into. The span
     * would then end {@code exception="none"} with no event and no ERROR status, both scrubs could be deleted, and this
     * assertion would still pass. Asserting the carriers are present is the same fix as asserting the HTTP quintet is
     * present: a subset relation says nothing until something is in the set.
     */
    @Test
    void noSentinelEverReachesAnExporter() throws Exception {

        // given
        driveRepresentativeEndpoints();
        var spans = finishedSpans();

        // when
        var actual = rendered(spans);

        // then
        assertThat(spans)
                .as("no span carried an ERROR status with a blanked description: either nothing exercised "
                        + "the status-description scrub, or the scrub ran and left the description in place")
                .anySatisfy(span -> {
                    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
                    assertThat(span.getStatus().getDescription()).isEmpty();
                });
        assertThat(spans).as("no span carried an exception event, so nothing exercised the event scrub")
                .anySatisfy(span -> assertThat(span.getEvents())
                        .anySatisfy(event -> assertThat(event.getName()).isEqualTo("exception")));
        assertThat(actual).doesNotContain(SENTINEL_EMAIL).doesNotContain(SENTINEL_NAME);
    }

    /**
     * Subset in one direction, and the HTTP quintet in the other. Without the second assertion a convention change that
     * renamed every server-span key would black out HTTP telemetry entirely and this test would still pass, since an
     * empty key set is trivially a subset of the allowlist.
     *
     * <p>
     * {@code isNotEmpty} sits behind the route wait rather than in front of it, so it no longer fires first on a
     * vacuous run - the wait does, and it names which route is missing. It is kept as the backstop for the case where
     * spans arrive carrying no attributes at all.
     */
    @Test
    void everyEmittedAttributeKeyIsOnTheAllowlist() throws Exception {

        // given
        driveRepresentativeEndpoints();

        // when
        var actual = finishedSpans().stream().flatMap(span -> span.getAttributes().asMap().keySet().stream())
                .map(key -> key.getKey()).distinct().toList();

        // then
        assertThat(actual).isNotEmpty();
        assertThat(actual).containsAll(HTTP_QUINTET);
        assertThat(actual).allSatisfy(key -> assertThat(AttributeAllowlist.PERMITTED).contains(key));
    }

    /**
     * The scrub runs inside the {@code CompositeSpanExporter} that Boot's {@code otelSpanProcessor} builds, so an
     * exporter reached through any other processor bypasses it. Two ordinary routes lead there: {@code spanProcessors}
     * collects every {@code SpanProcessor} bean into the tracer provider, and {@code otelSpanProcessor} is
     * {@code ConditionalOnMissingBean}, so a hand-declared {@code BatchSpanProcessor} replaces Boot's composite
     * outright. Both are the shape an "add a debug exporter" snippet takes, and neither would be visible to any other
     * assertion here, because the guard's own exporter goes through the composite.
     */
    @Test
    void nothingElseRegistersASpanProcessor() {

        // when
        var actual = context.getBeanNamesForType(SpanProcessor.class);

        // then
        assertThat(actual)
                .as("every SpanProcessor bean outside this set reaches an exporter without passing either scrubber; "
                        + "if a Boot upgrade renamed one, re-read OpenTelemetryTracingAutoConfiguration before "
                        + "updating the set")
                .containsExactlyInAnyOrder("otelSpanProcessor", "spanStatusScrubbingProcessor");
    }

    /**
     * {@code http.send} returns once the client has read the response, but the server span is stopped by
     * {@code ServerHttpObservationFilter} after {@code chain.doFilter} returns, so a flush issued the instant the last
     * call returns can miss the very span this test exists to inspect. Waiting on the routes rather than on a count
     * makes the wait specific: it ends when all four are present, and times out red rather than passing on three.
     * {@code untilAsserted} rather than {@code until}, so the timeout carries AssertJ's diff naming the routes that
     * never arrived instead of only saying a lambda went unsatisfied.
     */
    private List<SpanData> finishedSpans() {

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(routesSeen()).containsAll(EXPECTED_ROUTES));
        return exporter.getFinishedSpanItems();
    }

    private Set<String> routesSeen() {

        // The production processor is a BatchSpanProcessor: without a flush the assertions race its scheduled delay.
        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        return exporter.getFinishedSpanItems().stream().map(span -> span.getAttributes().get(ROUTE))
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private String rendered(List<SpanData> spans) {

        return spans.toString();
    }

    /**
     * Four request shapes, each carrying a different class of identifier: the authenticated principal, free text in a
     * request body, a handle in the URI of a request that is refused, and a handle inside the message of an exception
     * that escapes the dispatcher.
     *
     * <p>
     * The list read carries the token, so it is what proves the tenant stamp is the internal UUID rather than the
     * {@code email} or {@code name} claim sitting beside it on the same token. The dismissal is a real write whose
     * body's free text is persisted, so nothing about it is a dry run. The third exports {@code http.url}, the
     * request's own path, sentinel handle and all, on a build with the attribute scrub removed. The fourth is the only
     * one that reaches the span's events and its status description, because every other request here ends
     * {@code exception="none"} - {@code GlobalExceptionHandler} converts a domain refusal inside the dispatcher, so
     * even the 404 is an outcome rather than an error.
     *
     * <p>
     * Each status is pinned, because all four would still emit a span if the seeding or the stubbed decoder stopped
     * working and every call became a 401. That guard would pass on spans of a shape nobody meant to test.
     */
    private void driveRepresentativeEndpoints() throws Exception {

        assertThat(get("/axonia/communities").statusCode()).isEqualTo(200);
        assertThat(post("/axonia/expertise/me/dismiss",
                "{\"sourceType\":\"ARTIFACT\",\"sourceId\":1,\"tag\":\"" + SENTINEL_NAME + "\"}").statusCode())
                .isEqualTo(204);
        assertThat(get("/axonia/communities/" + SENTINEL_EMAIL).statusCode()).isEqualTo(404);
        assertThat(get("/axonia/telemetry-guard-probe/" + SENTINEL_EMAIL).statusCode()).isEqualTo(500);
    }

    private HttpResponse<String> get(String path) throws Exception {

        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {

        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {

        return http.send(request.header("Authorization", "Bearer forged-but-decodable").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {

        return URI.create("http://localhost:" + port + path);
    }

    @TestConfiguration
    static class GuardProbeConfiguration {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {

            return InMemorySpanExporter.create();
        }

        @Bean
        TelemetryGuardProbeController telemetryGuardProbeController() {

            return new TelemetryGuardProbeController();
        }
    }

    /**
     * Nothing in the real application throws on purpose, and an exception that escapes the dispatcher is the only way
     * to reach {@code span.error(throwable)}. Mapped under {@code /axonia/**} so it authenticates like any other route.
     *
     * <p>
     * The message quotes a sentinel deliberately, which is the opposite of what {@code RequestTelemetryInstalledTest}'s
     * probe does. It is modelled on the real case: a duplicate insert on {@code identity_user} surfaces as Spring's
     * {@code DuplicateKeyException}, whose message embeds Postgres' own text naming the offending column values. That
     * message reaches the span's exception event and its status description, and both assertions above are what say it
     * goes no further. It reaches this application's own log record too, on the line {@code RequestTelemetryFilter}
     * writes - a different surface, governed by what that filter logs rather than by this allowlist.
     */
    @RestController
    @RequestMapping("/${spring.application.name}")
    static class TelemetryGuardProbeController {

        @GetMapping("/telemetry-guard-probe/{handle}")
        String escapesTheDispatcher(@PathVariable String handle) {

            throw new IllegalStateException(
                    "Key (organization_id, email)=(" + ORGANIZATION + ", " + handle + ") already exists");
        }
    }
}
