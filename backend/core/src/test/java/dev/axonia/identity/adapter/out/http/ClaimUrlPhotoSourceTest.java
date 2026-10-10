package dev.axonia.identity.adapter.out.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import dev.axonia.identity.application.AvatarFetchFailedException;
import dev.axonia.identity.application.AvatarHostPolicy;
import dev.axonia.identity.configuration.AvatarProperties;
import dev.axonia.shared.api.metrics.IntegrationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fetch is exercised against a real loopback HTTP server rather than a mock, because what is being tested is the
 * behaviour of the transport under hostile responses - an oversized body, a redirect, a wrong content type - and a
 * mocked client would only prove the test's own assumptions about it.
 *
 * <p>
 * A policy permitting {@code localhost} is used so the transport can be reached at all. The address-range rejection is
 * therefore disabled for these tests via the constructor flag, and is covered by its own test below.
 */
class ClaimUrlPhotoSourceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    /**
     * Port 1 is a privileged port: only a root process can bind it, so no unprivileged process on the machine - another
     * test, a Testcontainers-published port, a surefire fork - can ever be listening there. Connecting to it therefore
     * gets an immediate connection-refused on both macOS and Linux rather than a slow connect-timeout, and does so
     * deterministically, unlike a {@code ServerSocket(0)} opened and closed just before the fetch: closing the socket
     * releases the ephemeral port back to the OS, and anything else on the machine can rebind that exact port before
     * the fetch runs, which is a real, observed flake under full-gate load.
     */
    private static final String UNREACHABLE_URL = "http://localhost:1/nothing";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final IntegrationMetrics metrics = new IntegrationMetrics(registry);

    private HttpServer server;
    private AvatarProperties properties;
    private ClaimUrlPhotoSource source;

    @BeforeEach
    void startServer() throws IOException {

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        properties = new AvatarProperties(List.of("localhost"), 1024, Duration.ofSeconds(2));
        // The transport cases below need a real server, and a real server here speaks plain HTTP, which the
        // production policy refuses on scheme. Overriding the policy rather than adding a production flag keeps
        // the https requirement unconditional in the code that ships. The real policy is exercised by the
        // refusal case below, and by AvatarHostPolicyTest.
        source = new ClaimUrlPhotoSource(permissive(properties), properties, metrics, false);
    }

    private static AvatarHostPolicy permissive(AvatarProperties properties) {

        return new AvatarHostPolicy(properties) {
            @Override
            public boolean permits(URI uri) {
                return true;
            }
        };
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
    }

    private void respond(String path, int status, String contentType, byte[] body) {

        server.createContext(path, exchange -> {
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Test
    void refusesAUrlThePolicyDoesNotPermit() {

        var guarded = new ClaimUrlPhotoSource(new AvatarHostPolicy(properties), properties, metrics, false);

        assertThat(guarded.fetch("https://cdn.example.com/a.png")).isEmpty();
    }

    @Test
    void refusesAMalformedUrl() {

        assertThat(source.fetch("not a url at all")).isEmpty();
    }

    @Test
    void refusesNull() {

        assertThat(source.fetch(null)).isEmpty();
    }

    @Test
    void refusesANonImageContentType() {

        respond("/text", 200, "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8));

        assertThat(source.fetch(url("/text"))).isEmpty();
    }

    @Test
    void refusesABodyLargerThanTheCeiling() {

        respond("/big", 200, "image/png", new byte[2048]);

        assertThat(source.fetch(url("/big"))).isEmpty();
    }

    @Test
    void refusesANonSuccessStatus() {

        respond("/gone", 404, "image/png", new byte[0]);

        assertThat(source.fetch(url("/gone"))).isEmpty();
    }

    @Test
    void refusesAnEmptyBody() {

        respond("/empty", 200, "image/png", new byte[0]);

        assertThat(source.fetch(url("/empty"))).isEmpty();
    }

    @Test
    void throwsOnAServerError() {

        respond("/error", 500, "image/png", new byte[0]);

        assertThatThrownBy(() -> source.fetch(url("/error"))).isInstanceOf(AvatarFetchFailedException.class);
    }

    @Test
    void throwsOnTooManyRequests() {

        respond("/limited", 429, "image/png", new byte[0]);

        assertThatThrownBy(() -> source.fetch(url("/limited"))).isInstanceOf(AvatarFetchFailedException.class);
    }

    @Test
    void refusesAHostThatDoesNotResolve() {

        // .invalid is reserved by RFC 2606 and is guaranteed to never resolve.
        var guarded = new ClaimUrlPhotoSource(permissive(properties), properties, metrics, true);

        assertThat(guarded.fetch("http://avatar-host-that-does-not-exist.invalid/a")).isEmpty();
    }

    @Test
    void permitsAResolvedPublicAddressWhenTheGuardIsOn() {

        // 203.0.113.0/24 (TEST-NET-3, RFC 5737) is reserved for documentation: an IP literal needs no DNS lookup,
        // is never routable so the connection that follows always fails fast, but is not loopback, site-local,
        // link-local or multicast either - so the address-is-public check itself must pass before that happens.
        var guarded = new ClaimUrlPhotoSource(permissive(properties), properties, metrics, true);

        assertThatThrownBy(() -> guarded.fetch("http://203.0.113.5/a")).isInstanceOf(AvatarFetchFailedException.class);
    }

    @Test
    void throwsWhenTheConnectionIsRefused() {

        assertThatThrownBy(() -> source.fetch(UNREACHABLE_URL)).isInstanceOf(AvatarFetchFailedException.class);
    }

    @Test
    void throwsWhenTheThreadIsInterruptedAndRestoresTheFlag() {

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> source.fetch(url("/interrupted"))).isInstanceOf(AvatarFetchFailedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void returnsThePhotoWhenEverythingIsInOrder() {

        respond("/ok", 200, "image/png", PNG);

        var photo = source.fetch(url("/ok"));

        assertThat(photo).isPresent();
        assertThat(photo.get().contentType()).isEqualTo("image/png");
        assertThat(photo.get().bytes()).isEqualTo(PNG);
    }

    @Test
    void rejectsAnAddressOutsideThePublicRangesWhenTheGuardIsOn() {

        var guarded = new ClaimUrlPhotoSource(permissive(properties), properties, metrics, true);
        respond("/local", 200, "image/png", PNG);

        assertThat(guarded.fetch(url("/local"))).isEmpty();
    }

    private void redirectTo(String path, String location) {

        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    @Test
    void followsOneRedirectHopToAValidImage() {

        respond("/final", 200, "image/png", PNG);
        redirectTo("/redirect-once", url("/final"));

        var photo = source.fetch(url("/redirect-once"));

        assertThat(photo).isPresent();
        assertThat(photo.get().bytes()).isEqualTo(PNG);
    }

    @Test
    void doesNotFollowASecondRedirectHop() {

        respond("/final", 200, "image/png", PNG);
        redirectTo("/hop-two", url("/final"));
        redirectTo("/hop-one", url("/hop-two"));

        assertThat(source.fetch(url("/hop-one"))).isEmpty();
    }

    @Test
    void refusesARedirectWithNoLocationHeader() {

        server.createContext("/nowhere", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThat(source.fetch(url("/nowhere"))).isEmpty();
    }

    @Test
    void refusesAMalformedRedirectTarget() {

        redirectTo("/bad-redirect", "http://[not-a-valid-ipv6-literal");

        assertThat(source.fetch(url("/bad-redirect"))).isEmpty();
    }

    @Test
    void refusesARedirectDestinationThePolicyDoesNotPermit() {

        var originalPath = "/redirect-refused";
        var policy = new AvatarHostPolicy(properties) {
            @Override
            public boolean permits(URI uri) {
                return originalPath.equals(uri.getPath());
            }
        };
        var guarded = new ClaimUrlPhotoSource(policy, properties, metrics, false);
        respond("/final-refused", 200, "image/png", PNG);
        redirectTo(originalPath, url("/final-refused"));

        assertThat(guarded.fetch(url(originalPath))).isEmpty();
    }

    /**
     * This class is the one outbound client in the application that is not a {@code RestClient}, so nothing else in the
     * telemetry would have noticed it going dark: an egress policy change that broke every avatar fetch would leave six
     * healthy integrations on the dashboard and a member eventually asking why avatars stopped updating.
     */
    @Test
    void countsAFetchThatAnsweredAsOneCall() {

        respond("/counted-ok", 200, "image/png", PNG);

        assertThat(source.fetch(url("/counted-ok"))).isPresent();

        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
    }

    @Test
    void countsAHostThatCannotBeReachedAsAFailedCall() {

        assertThatThrownBy(() -> source.fetch(UNREACHABLE_URL)).isInstanceOf(AvatarFetchFailedException.class);

        assertThat(calls("failure")).isEqualTo(1.0);
    }

    /** The two reply shapes this class already treats as retryable are raised at the send, and so counted there. */
    @Test
    void countsATooManyRequestsReplyAsAFailedCall() {

        respond("/counted-limited", 429, "image/png", new byte[0]);

        assertThatThrownBy(() -> source.fetch(url("/counted-limited"))).isInstanceOf(AvatarFetchFailedException.class);

        assertThat(calls("failure")).isEqualTo(1.0);
    }

    /**
     * The hop is a second request over the wire, so it is a second counted call rather than extra latency hidden inside
     * the first.
     */
    @Test
    void countsARedirectAndItsDestinationAsTwoSeparateCalls() {

        respond("/counted-final", 200, "image/png", PNG);
        redirectTo("/counted-redirect", url("/counted-final"));

        assertThat(source.fetch(url("/counted-redirect"))).isPresent();

        assertThat(calls("success")).isEqualTo(2.0);
    }

    /** A URL the policy refuses never reaches the wire, so counting it would report a call that never happened. */
    @Test
    void neverCountsAUrlRefusedBeforeAnyByteLeft() {

        var guarded = new ClaimUrlPhotoSource(new AvatarHostPolicy(properties), properties, metrics, false);

        assertThat(guarded.fetch("https://cdn.example.com/a.png")).isEmpty();

        assertThat(calls("success")).isZero();
        assertThat(calls("failure")).isZero();
    }

    /**
     * The failure this counter exists for on this integration, and the one with no other signal. A host answering 403
     * to everything makes every fetch answer empty, and {@code AvatarSyncService} answers empty by clearing the stored
     * avatar - so the estate's claim-sourced avatars are wiped one tick at a time, off the request path, with nothing
     * else moving. The empty answer is asserted alongside the counter: the meter's split changed, the caller's did not.
     */
    @Test
    void countsAHostRefusalAsAFailedCallWhileStillAnsweringEmpty() {

        respond("/counted-forbidden", 403, "image/png", new byte[0]);

        assertThat(source.fetch(url("/counted-forbidden"))).isEmpty();

        assertThat(calls("failure")).isEqualTo(1.0);
        assertThat(calls("success")).isZero();
    }

    /**
     * The other half of the split: the photo being gone is a finding about the photo, not the host refusing, so it
     * counts as an answered call. Same empty result to the caller as the 403 above, opposite outcome on the meter.
     */
    @Test
    void countsAPhotoThatIsGoneAsAnAnsweredCallWhileStillAnsweringEmpty() {

        respond("/counted-missing", 404, "image/png", new byte[0]);

        assertThat(source.fetch(url("/counted-missing"))).isEmpty();

        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
    }

    /** 410 says the same thing as 404 for a photo that used to be there, and is classified the same way. */
    @Test
    void countsAGoneReplyAsAnAnsweredCallToo() {

        respond("/counted-gone", 410, "image/png", new byte[0]);

        assertThat(source.fetch(url("/counted-gone"))).isEmpty();

        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isZero();
    }

    private double calls(String outcome) {

        return registry.get("axonia.integration.calls").tag("integration", "avatar").tag("outcome", outcome).counter()
                .count();
    }
}
