package dev.axonia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Mailpit of the tests' own, never the developer's on 1025. Each variant is one container, started the first time it
 * is asked for and shared by the JVM after that; Ryuk or the JVM's exit stops it.
 */
public final class Mailpit {

    public static final String IMAGE = "axllent/mailpit:v1.28";

    /** The login that {@link #authenticating()} requires. */
    public static final String AUTH_USERNAME = "relay";

    public static final String AUTH_PASSWORD = "relay-s3cret";

    private static final int SMTP_PORT = 1025;
    private static final int API_PORT = 8025;
    private static final int OK = 200;

    private final GenericContainer<?> container;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    private Mailpit(Map<String, String> environment) {

        container = new GenericContainer<>(IMAGE).withExposedPorts(SMTP_PORT, API_PORT).withEnv(environment)
                .waitingFor(Wait.forHttp("/api/v1/info").forPort(API_PORT).forStatusCode(OK)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        container.start();
    }

    /** Accepts every message, with any credential or none, over plain SMTP. */
    public static Mailpit open() {

        return Open.INSTANCE;
    }

    /**
     * Requires {@link #AUTH_USERNAME} and {@link #AUTH_PASSWORD} over plain SMTP and accepts recipients at acme.test
     * only. Replies probed on v1.28 (2026-10-04): {@code 535 5.7.8} for a wrong password, {@code 530 5.7.0
     * Authentication required} for none, {@code 550 5.1.0} for another domain.
     */
    public static Mailpit authenticating() {

        return Authenticating.INSTANCE;
    }

    /**
     * Refuses mail before STARTTLS ({@code 530 5.7.0 Must issue a STARTTLS command first}), with a self-signed
     * certificate.
     */
    public static Mailpit requiringStartTls() {

        return RequiringStartTls.INSTANCE;
    }

    public String host() {

        return container.getHost();
    }

    public int smtpPort() {

        return container.getMappedPort(SMTP_PORT);
    }

    /** Every message received since the last {@link #clear()}, newest first. */
    public List<Message> messages() {

        var body = json.readTree(call(HttpRequest.newBuilder(api("/api/v1/messages")).GET().build()));
        return StreamSupport.stream(body.path("messages").spliterator(), false).map(Mailpit::messageOf).toList();
    }

    public void clear() {

        call(HttpRequest.newBuilder(api("/api/v1/messages")).DELETE().build());
    }

    private URI api(String path) {

        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(API_PORT) + path);
    }

    private String call(HttpRequest request) {

        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != OK) {
                throw new IllegalStateException("Mailpit answered " + response.statusCode() + " to " + request.uri());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Mailpit could not be reached at " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while asking Mailpit", e);
        }
    }

    private static Message messageOf(JsonNode node) {

        var to = StreamSupport.stream(node.path("To").spliterator(), false)
                .map(address -> address.path("Address").asString()).toList();
        return new Message(node.path("From").path("Address").asString(), node.path("From").path("Name").asString(), to,
                node.path("Subject").asString());
    }

    public record Message(String from, String fromName, List<String> to, String subject) {
    }

    private static final class Open {

        private static final Mailpit INSTANCE = new Mailpit(
                Map.of("MP_SMTP_AUTH_ACCEPT_ANY", "true", "MP_SMTP_AUTH_ALLOW_INSECURE", "true"));
    }

    private static final class Authenticating {

        private static final Mailpit INSTANCE = new Mailpit(Map.of("MP_SMTP_AUTH", AUTH_USERNAME + ":" + AUTH_PASSWORD,
                "MP_SMTP_AUTH_ALLOW_INSECURE", "true", "MP_SMTP_ALLOWED_RECIPIENTS", "@acme\\.test$"));
    }

    private static final class RequiringStartTls {

        private static final Mailpit INSTANCE = new Mailpit(Map.of("MP_SMTP_TLS_CERT", "sans:localhost",
                "MP_SMTP_TLS_KEY", "sans:localhost", "MP_SMTP_REQUIRE_STARTTLS", "true"));
    }
}
