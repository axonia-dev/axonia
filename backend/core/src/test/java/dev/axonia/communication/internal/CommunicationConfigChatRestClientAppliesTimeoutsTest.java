package dev.axonia.communication.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.axonia.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * {@link CommunicationConfigChatRestClientIsLazyTest} proves the bean stays lazy; the redirect test in
 * {@link ChatTransportTest} builds it from {@code HttpClientSettings.defaults()} to exercise redirect policy in
 * isolation. Neither proves the one thing {@link CommunicationConfig#chatRestClient}'s own javadoc calls load-bearing:
 * a client actually carrying the connect and read ceilings {@code spring.http.clients.*} resolves to. An edit that
 * swapped the injected {@code RestClient.Builder} for a static {@code RestClient.builder()} - the exact mistake that
 * javadoc warns about - would leave every other test in this module green while silently making both timeouts infinite,
 * which is a database connection pinned for as long as a stalled tenant endpoint cares to hold it.
 *
 * <p>
 * This is a real, observable-behaviour assertion, not a reflection compromise: {@link #contextRunner} runs the actual
 * Spring Boot auto-configuration chain ({@link HttpClientAutoConfiguration} binds {@code HttpClientsProperties} into a
 * real {@link HttpClientSettings}, {@link ImperativeHttpClientAutoConfiguration} resolves the real
 * {@code ClientHttpRequestFactoryBuilder}, {@link RestClientAutoConfiguration} the real {@code RestClient.Builder}) -
 * never a hand-built {@code HttpClientSettings.of(...)} - so what is bound here is provably the same object production
 * gets from {@code spring.http.clients.connect-timeout} / {@code read-timeout}. The test values are short rather than
 * the shipped 2s/10s purely so the test itself stays fast; the binding path is identical either way.
 *
 * <p>
 * The scenario is a read timeout - a server that accepts the TCP connection (so this is genuinely not a connect
 * failure) and then never answers - because that is precisely the shape {@link ChatTransport}'s javadoc worries about:
 * "every drain worker holds a database connection for the whole duration of its send". A connect timeout flows through
 * the identical {@link HttpClientSettings} and the identical request factory, so proving the read ceiling is honoured
 * proves the wiring is real; reliably forcing a TCP handshake itself to stall, rather than merely a response, would
 * need a host that black-holes SYN packets, which no hermetic test can arrange portably.
 * {@code assertTimeoutPreemptively} bounds the whole assertion well above the configured read timeout but well below
 * the point where a broken build would otherwise hang for the server thread's own five-second sleep, so a regression
 * fails fast with a clear timeout message rather than slowing the suite down.
 */
class CommunicationConfigChatRestClientAppliesTimeoutsTest {

    private static final Duration CONFIGURED_READ_TIMEOUT = Duration.ofMillis(400);
    private static final Duration TEST_BOUND = Duration.ofSeconds(3);
    private static final Duration SERVER_SILENCE = Duration.ofSeconds(5);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class, RestClientAutoConfiguration.class))
            .withPropertyValues("spring.http.clients.connect-timeout=400ms",
                    "spring.http.clients.read-timeout=" + CONFIGURED_READ_TIMEOUT.toMillis() + "ms");

    @Test
    void chatRestClientAppliesTheConfiguredReadTimeoutRatherThanHangingForever() throws IOException {

        contextRunner.run(context -> {
            var settings = context.getBean(HttpClientSettings.class);
            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofMillis(400));
            assertThat(settings.readTimeout()).isEqualTo(CONFIGURED_READ_TIMEOUT);

            var factoryBuilder = context.getBeanProvider(ClientHttpRequestFactoryBuilder.class).getObject();
            var chatRestClient = new CommunicationConfig().chatRestClient(context.getBean(RestClient.Builder.class),
                    settings, factoryBuilder, new IntegrationMetrics(new SimpleMeterRegistry()));

            try (var serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                var serverThread = acceptThenStaySilent(serverSocket);
                serverThread.start();

                var url = URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/hook");
                var start = Instant.now();

                assertTimeoutPreemptively(TEST_BOUND,
                        () -> assertThatThrownBy(() -> chatRestClient.post().uri(url).retrieve().toBodilessEntity())
                                .isInstanceOf(RestClientException.class));

                assertThat(Duration.between(start, Instant.now())).isLessThan(TEST_BOUND);
            }
        });
    }

    /** Accepts the one connection so the client's connect genuinely succeeds, then never writes a response. */
    private static Thread acceptThenStaySilent(ServerSocket serverSocket) {

        var thread = new Thread(() -> {
            try (var socket = serverSocket.accept()) {
                Thread.sleep(SERVER_SILENCE);
            } catch (IOException | InterruptedException ignored) {
                // Test server thread: the test's own assertions are what fail, not this best-effort stand-in.
            }
        });
        thread.setDaemon(true);
        return thread;
    }
}
