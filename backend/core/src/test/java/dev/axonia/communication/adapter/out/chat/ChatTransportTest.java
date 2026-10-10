package dev.axonia.communication.adapter.out.chat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sun.net.httpserver.HttpServer;

import dev.axonia.communication.api.ChatFailureKind;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.DrainWorkers;
import dev.axonia.communication.application.port.out.ChatSendException;
import dev.axonia.communication.configuration.CommunicationConfig;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class ChatTransportTest {

    private static final URI HOOK = URI.create("https://hooks.slack.com/services/T000/B000/x");
    private static final Map<String, Object> FACTS = Map.of("sessionTitle", "Event sourcing", "scheduledAt",
            Instant.parse("2026-09-01T16:00:00Z"), "sessionUrl", "https://axonia.example/s/1", "talks", List.of(),
            "sender", Map.of("name", "Backend Guild", "initials", "BG", "tintBackground", "#3b1e54", "tintForeground",
                    "#ffffff"));

    private MockRestServiceServer server;
    private ChatTransport sut;

    @BeforeEach
    void setUp() {

        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sut = new ChatTransport(builder.build(), new ChatPlatforms(List.of(new SlackPlatform(), new TeamsPlatform())),
                new ChatPostBuilders());
    }

    @Test
    void post_sendsTheRenderedPayloadToTheWebhook() {

        server.expect(requestTo(HOOK)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());

        sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS);

        server.verify();
    }

    @Test
    void post_treatsA404AsGone() {

        server.expect(requestTo(HOOK)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.GONE));
    }

    @Test
    void post_treatsA410AsGone() {

        server.expect(requestTo(HOOK)).andRespond(withStatus(HttpStatus.GONE));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.GONE));
    }

    @Test
    void post_treatsSlacksInvalidTokenBodyAsGone() {

        server.expect(requestTo(HOOK)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("invalid_token"));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.GONE));
    }

    @Test
    void post_treatsTeamsNoServiceBodyAsGone() {

        server.expect(requestTo(HOOK)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("no_service"));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.GONE));
    }

    @Test
    void post_treatsA429AsTransient() {

        server.expect(requestTo(HOOK)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.TRANSIENT));
    }

    @Test
    void post_treatsA503AsTransient() {

        server.expect(requestTo(HOOK)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.TRANSIENT));
    }

    /**
     * Not every {@link org.springframework.web.client.RestClientException} arrives through {@code onStatus}: a
     * connection that never got an HTTP status at all - reset, refused, timed out - reaches the transport as one too,
     * and must read as ordinary weather rather than as a revoked webhook.
     */
    @Test
    void post_treatsANetworkFailureAsTransient() {

        server.expect(requestTo(HOOK)).andRespond(request -> {
            throw new IOException("connection reset");
        });

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.TRANSIENT));
    }

    /**
     * The regression this guards: Spring's {@code ResourceAccessException} message is
     * {@code "I/O error on POST request for \"<uri>\": <ioMessage>"} - the query is stripped by Spring itself, but the
     * path is not, and a Slack or Teams webhook's entire credential lives in the path. This message is what
     * {@link DrainWorkers} hands to {@code ChatDestinationsPort#recordFailure}, which persists it into the
     * leader-visible {@code chat_last_error} column - so a webhook's own bearer token leaking into a database column is
     * exactly what a naive {@code networkFailure.getMessage()} would do on the very first connect timeout.
     */
    @Test
    void post_networkFailureMessageNamesTheHostNeverThePathTheSecretLivesIn() {

        server.expect(requestTo(HOOK)).andRespond(request -> {
            throw new IOException("connection reset");
        });

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class,
                e -> assertThat(e.getMessage()).contains("hooks.slack.com").doesNotContain("services", "T000", "B000"));
    }

    /**
     * The other half of {@code networkFailureMessage}'s ladder: a {@link RestClientException} with no root cause at all
     * - Spring never fails to set one on a genuine I/O failure, but nothing here may assume that - still names the
     * host, using the exception's own class instead.
     */
    @Test
    void post_networkFailureWithNoRootCauseStillNamesOnlyTheHost() {

        server.expect(requestTo(HOOK)).andRespond(request -> {
            throw new RestClientException("mock transport failure, no cause");
        });

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class,
                e -> assertThat(e.getMessage()).contains("hooks.slack.com").doesNotContain("services", "T000", "B000"));
    }

    /**
     * {@code bodyOf} reads the error body defensively for exactly this case: an error response whose body stream itself
     * fails mid-read must still classify as a failure - {@code kindOf} has to see SOME body, and an unreadable one is
     * the empty string it falls back to - rather than let the read failure escape {@link ChatTransport#post} as
     * something other than a {@link ChatSendException}.
     */
    @Test
    void post_treatsAnUnreadableErrorBodyAsTransientRatherThanThrowingWhileReadingIt() {

        server.expect(requestTo(HOOK)).andRespond(request -> unreadableBodyResponse(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> sut.post(HOOK, MessageType.SESSION_WRAP_UP, FACTS)).isInstanceOfSatisfying(
                ChatSendException.class, e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.TRANSIENT));
    }

    private static ClientHttpResponse unreadableBodyResponse(HttpStatus status) {

        return new ClientHttpResponse() {

            @Override
            public HttpStatus getStatusCode() {
                return status;
            }

            @Override
            public String getStatusText() {
                return status.getReasonPhrase();
            }

            @Override
            public void close() {
            }

            @Override
            public InputStream getBody() {
                return new InputStream() {

                    @Override
                    public int read() throws IOException {
                        throw new IOException("body stream broken");
                    }
                };
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }

    @Test
    void post_refusesAStoredUrlOutsideTheAllowlistWithoutSendingAnything() {

        assertThatThrownBy(() -> sut.post(URI.create("https://evil.test/hook"), MessageType.SESSION_WRAP_UP, FACTS))
                .isInstanceOfSatisfying(ChatSendException.class,
                        e -> assertThat(e.kind()).isEqualTo(ChatFailureKind.GONE));
        server.verify();
    }

    /**
     * {@code MockRestServiceServer} replaces the request factory outright, so it cannot show whether the REAL one -
     * {@link CommunicationConfig#chatRestClient} - follows a redirect: that is a property of the factory it never
     * exercises. This runs {@link ChatTransport} against a real loopback server instead, wired with a real
     * {@code chatRestClient}, so what is under test is the actual redirect policy that ships.
     *
     * <p>
     * {@code ChatPlatforms} requires HTTPS, which a loopback test server cannot speak without a certificate nobody
     * would trust, so the allowlist check itself is stood in for by a permissive override - the same shape
     * {@code ClaimUrlPhotoSourceTest} uses to test transport behaviour against a policy that is otherwise
     * unconditionally HTTPS-only. The allowlist itself is proven by
     * {@link #post_refusesAStoredUrlOutsideTheAllowlistWithoutSendingAnything} above; what this test proves is
     * independent of it - that a host the transport was willing to send to cannot walk itself past that allowlist with
     * a 302.
     */
    @Test
    void post_doesNotFollowARedirectFromAnAllowlistedHost() throws IOException {

        var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.start();
        try {
            var redirectTargetHits = new AtomicInteger();
            httpServer.createContext("/redirect-target", exchange -> {
                redirectTargetHits.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            var port = httpServer.getAddress().getPort();
            httpServer.createContext("/hook", exchange -> {
                exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/redirect-target");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });

            var realChatRestClient = new CommunicationConfig().chatRestClient(RestClient.builder(),
                    HttpClientSettings.defaults(), ClientHttpRequestFactoryBuilder.detect(),
                    new IntegrationMetrics(new SimpleMeterRegistry()));
            var allowlistedForThisTest = new ChatPlatforms(List.of(new SlackPlatform())) {
                @Override
                Optional<ChatPlatform> platformFor(String url) {
                    return Optional.of(new SlackPlatform());
                }
            };
            var realTransport = new ChatTransport(realChatRestClient, allowlistedForThisTest, new ChatPostBuilders());

            assertThatCode(() -> realTransport.post(URI.create("http://127.0.0.1:" + port + "/hook"),
                    MessageType.SESSION_WRAP_UP, FACTS)).doesNotThrowAnyException();

            assertThat(redirectTargetHits).hasValue(0);
        } finally {
            httpServer.stop(0);
        }
    }
}
