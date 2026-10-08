package dev.axonia.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full context on a random port with the gate switched on and drives real HTTP through the actual servlet
 * filter chain.
 *
 * <p>
 * {@link EdgeGateWiringTest} proves the bean exists inside an isolated {@code WebApplicationContextRunner}, which is a
 * different claim: it says the {@code @ConditionalOnProperty} and the ordering are right, not that Spring Boot ever
 * installs the filter in the chain a request travels. Every other {@code @SpringBootTest} and {@code @WebMvcTest} in
 * this suite runs with {@code gate-enabled=false}, so without this class the registration could be dropped entirely and
 * the whole suite would stay green while the origin sat open.
 *
 * <p>
 * Verified to bite: removing {@code registration.setOrder(Ordered.HIGHEST_PRECEDENCE)} or the registration bean itself
 * fails {@link #refusesARequestThatDidNotComeThroughOurEdge()}.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "axonia.edge.gate-enabled=true", "axonia.edge.shared-secrets=" + EdgeGateInstalledTest.SECRET})
@ActiveProfiles("DEV")
class EdgeGateInstalledTest {

    static final String SECRET = "booted-context-edge-secret";

    private static final String GATED_PATH = "/axonia/access/me";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void refusesARequestThatDidNotComeThroughOurEdge() throws Exception {

        var response = get(GATED_PATH, null);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    /**
     * The platform's health probe is service-internal and never traverses the edge, so it must stay reachable.
     * {@code /actuator/health/readiness} is the one the platform calls, and 200 is the claim that matters: the gate
     * lets it through <em>and</em> readiness is insulated from the dependencies the aggregate reports on. This suite
     * runs with Redis unreachable, so a readiness group that had been widened to include it would fail right here.
     */
    @Test
    void letsThePlatformHealthProbeThrough() throws Exception {

        var response = get("/actuator/health/readiness", null);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    /**
     * The gate exempts three exact paths, and the exemption is about reachability rather than verdict. The aggregate is
     * legitimately 503 here because Redis is unreachable in this suite, so this asserts what the gate is responsible
     * for: not 403. A 403 would mean the origin had closed a path the platform is expected to reach.
     */
    @Test
    void exemptsEveryHealthPathFromTheGateWhateverTheirVerdict() throws Exception {

        for (var path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            assertThat(get(path, null).statusCode()).as("gate must not refuse %s", path).isNotEqualTo(403);
        }
    }

    /**
     * 401 rather than 403 is the whole point: it proves the gate passed the request on to Spring Security instead of
     * refusing it, which a 403 from either layer would otherwise hide.
     */
    @Test
    void letsAStampedRequestReachTheSecurityChain() throws Exception {

        var response = get(GATED_PATH, SECRET);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path, String edgeSecret) throws Exception {

        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (edgeSecret != null) {
            request.header("X-Axonia-Edge-Auth", edgeSecret);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
