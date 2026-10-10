package dev.axonia.shared.adapter.in.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.axonia.CoreSpringBootTest;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Boots the full context on a random port and drives real HTTP through the actual servlet filter chain, because that is
 * the only thing that proves {@link RequestTelemetryFilter} is installed rather than merely constructed, and the only
 * thing that proves it runs <em>behind</em> Spring Security.
 *
 * <p>
 * The tenant assertion is what carries that second claim. Both identifiers come from the SecurityContext, which Spring
 * Security populates inside its own filter and clears on the way out, so an MDC carrying them can only have been
 * written by a filter nested inside the security chain. Registering the filter one step earlier makes
 * {@link #stampsTheTenantOnWhatItLogs()} fail with two nulls rather than pass quietly.
 *
 * <p>
 * The probe controller exists because nothing in the real application throws on purpose. It is mapped under
 * {@code /axonia/**} so it is subject to authentication like every other route, and its path carries a handle so the
 * templated-route claim is testable against a URI that must not appear in the log.
 *
 * <p>
 * The appender is attached to the <em>root</em> logger, not to the filter's own. Attaching it to the filter's logger
 * would have made every count below a statement about this one class rather than about what leaves the process, and the
 * container's second record - the whole subject of {@link #shipsOneTenantedRecordPlusTheContainersUntenantedCopy()} -
 * would have been invisible.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RequestTelemetryInstalledTest.ProbeConfiguration.class)
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class RequestTelemetryInstalledTest {

    private static final String ORGANIZATION = "request-telemetry-acme";

    private static final String USER = "9f3c1d20-0b8a-4f66-9a1e-2d5f0c7a4b11";

    private static final String HANDLE = "ada.lovelace";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private OrganizationRegistry organizations;

    private static final AtomicBoolean SEEDED = new AtomicBoolean();

    /**
     * Stands in for the resource-server decoder so a forged bearer authenticates without reaching the identity
     * provider.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient http = HttpClient.newHttpClient();

    private final ListAppender<ILoggingEvent> logs = new MdcCapturingAppender();

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    /**
     * The Organization has to exist and be ACTIVE, because {@code OrganizationLifecycleFilter} answers 401 for one that
     * does not - and it sits downstream of this filter, so without a real registry row every probe below would be
     * refused before it ever reached a handler.
     */
    @BeforeEach
    void seedTheTenantStubTheTokenAndCaptureLogs() {

        if (SEEDED.compareAndSet(false, true)) {
            OrganizationScope.runAs(ORGANIZATION, () -> organizations.register(OrganizationRegistration.builder()
                    .id(ORGANIZATION).name("Request Telemetry Acme").kind(OrganizationKind.ENTERPRISE).build()));
        }
        when(jwtDecoder.decode(anyString()))
                .thenReturn(Jwt.withTokenValue("token").header("alg", "none").subject(USER).claim("org", ORGANIZATION)
                        .claim("name", "Ada Lovelace").claim("email", "ada@request-telemetry.example").build());
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {

        rootLogger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void stampsTheTenantOnWhatItLogs() throws Exception {

        get("/axonia/request-telemetry-probe/" + HANDLE);

        assertThat(escapes()).singleElement().satisfies(event -> assertThat(event.getMDCPropertyMap())
                .containsEntry("organization_id", ORGANIZATION).containsEntry("user_id", USER));
    }

    /**
     * The mitigation is over the message <em>template</em>: the route is templated, so no handle reaches the record
     * through the path. What a thrower puts in its own message stays the thrower's responsibility, and the appender
     * ships that alongside - which is why the probe's message is deliberately handle-free rather than relying on this
     * assertion to catch it.
     */
    @Test
    void logsTheTemplatedRouteAndLeavesTheStatusCodeAlone() throws Exception {

        var response = get("/axonia/request-telemetry-probe/" + HANDLE);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(escapes()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("Unhandled failure serving GET /axonia/request-telemetry-probe/{handle}"));
    }

    /**
     * The rethrow is what keeps the status code honest, and its price is a second record: Tomcat's
     * {@code StandardWrapperValve} logs its own canonical line once the exception reaches it, through the JUL bridge,
     * by which time this filter's {@code finally} has cleared the MDC. So the wire carries two ERROR records per
     * escape, one tenanted and one not, and that is pinned here rather than left to be discovered in Loki. Silencing
     * the container would mean hardcoding a version-dependent Tomcat logger name; see the filter's javadoc.
     */
    @Test
    void shipsOneTenantedRecordPlusTheContainersUntenantedCopy() throws Exception {

        get("/axonia/request-telemetry-probe/" + HANDLE);

        assertThat(errors()).hasSize(2);
        assertThat(errors()).filteredOn(event -> !RequestTelemetryFilter.class.getName().equals(event.getLoggerName()))
                .singleElement()
                .satisfies(event -> assertThat(event.getMDCPropertyMap()).doesNotContainKey("organization_id"));
    }

    /**
     * The empirical half of the claim this filter's javadoc makes: {@code GlobalExceptionHandler} converts a domain
     * exception inside {@code DispatcherServlet.doDispatch}, so the throw never reaches this frame and a 404 is never
     * reported as an incident. If that ordering were ever the other way round, every not-found would page someone.
     */
    @Test
    void doesNotReportADomainRefusalAsAnIncident() throws Exception {

        var response = get("/axonia/request-telemetry-probe-not-found/" + HANDLE);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(errors()).isEmpty();
    }

    /**
     * Pins the registration between two real beans rather than against the constant it is derived from, so it bites on
     * both sides. Later than Spring Security means nested inside its chain, which is what makes the tenant resolvable.
     * Earlier than {@code OrganizationLifecycleFilter} is what keeps the tenant filters' own refusals attributable - a
     * bare "later than security" assertion would pass at {@code LOWEST_PRECEDENCE}, where every 401 for an unknown
     * Organization would go out untenanted.
     */
    @Test
    void registersBetweenTheSecurityChainAndTheTenantFilters() {

        assertThat(orderOf("requestTelemetryRegistration")).isGreaterThan(orderOf("securityFilterChainRegistration"))
                .isLessThan(orderOf("organizationLifecycleFilter"));
    }

    private int orderOf(String beanName) {

        return context.getBean(beanName, Ordered.class).getOrder();
    }

    private List<ILoggingEvent> errors() {

        return logs.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    private List<ILoggingEvent> escapes() {

        return errors().stream().filter(event -> RequestTelemetryFilter.class.getName().equals(event.getLoggerName()))
                .toList();
    }

    private HttpResponse<String> get(String path) throws Exception {

        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET()
                .header("Authorization", "Bearer forged-but-decodable").build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Logback fills a {@code LoggingEvent}'s MDC map lazily, off whichever thread asks for it first. The request runs
     * on a container thread and the assertion on the test's own, so without this the captured map would be the test
     * thread's - empty - rather than the request's.
     */
    private static final class MdcCapturingAppender extends ListAppender<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {

            event.prepareForDeferredProcessing();
            super.append(event);
        }
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        TelemetryProbeController telemetryProbeController() {

            return new TelemetryProbeController();
        }
    }

    @RestController
    @RequestMapping("/${spring.application.name}")
    static class TelemetryProbeController {

        /** The message names no handle on purpose: the appender ships a throwable's own message with the record. */
        @GetMapping("/request-telemetry-probe/{handle}")
        String escapesTheDispatcher() {

            throw new IllegalStateException("probe failure");
        }

        @GetMapping("/request-telemetry-probe-not-found/{handle}")
        String refusedByTheDomain() {

            throw new NotFoundException("no such probe");
        }
    }
}
