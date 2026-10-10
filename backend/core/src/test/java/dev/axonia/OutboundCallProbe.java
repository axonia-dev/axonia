package dev.axonia;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import dev.axonia.shared.api.metrics.IntegrationMetrics;
import dev.axonia.shared.api.metrics.MeterContribution;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * A real loopback server and a real registry, for the tests that prove a {@code RestClient} bean actually carries
 * {@link IntegrationMetrics}'s interceptor.
 *
 * <p>
 * Real HTTP rather than a mocked request factory: the thing under test is that the interceptor is attached to the bean
 * the configuration class builds, and an assertion that never sends a request would pass against a client that had lost
 * it. Shared from the root test package because the five client beans live in three different modules behind
 * package-private bean methods, so no single test class can reach them all.
 *
 * <p>
 * {@link #refused()} answers 401 - the shape of a revoked or de-scoped credential, and the reason a 4xx counts as a
 * failure at all.
 *
 * <p>
 * {@link #baseUrl()} also answers any POST under {@code /realms/} with a five-minute token in Keycloak's shape, for a
 * client that authorizes every call it sends.
 *
 * <p>
 * It meters the open integrations; a test of a module's own client hands in that module's {@link MeterContribution}, as
 * the application context does, so this probe never names a module's tag itself.
 */
public final class OutboundCallProbe implements AutoCloseable {

    private final HttpServer server;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final IntegrationMetrics metrics;

    public OutboundCallProbe(MeterContribution... contributions) throws IOException {

        metrics = new IntegrationMetrics(registry, List.of(contributions));
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/refused", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.createContext("/realms/", exchange -> {
            var body = "{\"access_token\":\"probe-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
        server.start();
    }

    public IntegrationMetrics metrics() {

        return metrics;
    }

    /** Absolute, so it overrides whatever base URL the bean under test was built with. */
    public URI ok() {

        return uri("/ok");
    }

    public URI refused() {

        return uri("/refused");
    }

    public String baseUrl() {

        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public double calls(String integration, String outcome) {

        return registry.get("axonia.integration.calls").tag("integration", integration).tag("outcome", outcome)
                .counter().count();
    }

    @Override
    public void close() {

        server.stop(0);
    }

    private URI uri(String path) {

        return URI.create(baseUrl() + path);
    }
}
