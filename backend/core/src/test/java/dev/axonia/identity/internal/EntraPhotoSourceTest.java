package dev.axonia.identity.internal;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.axonia.IntegrationMetrics;
import dev.axonia.SteppableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EntraPhotoSourceTest {

    private static final String GRAPH_BASE = EntraGraph.GRAPH_BASE;
    private static final String METADATA_ENDPOINT = GRAPH_BASE + "/v1.0/users/entra-1/photo";
    private static final String BYTES_ENDPOINT = GRAPH_BASE + "/v1.0/users/entra-1/photos/240x240/$value";
    private static final String TENANT = "tenant-default";

    /** Years from the wall clock, so only a token whose lifetime runs on this clock can expire on it. */
    private static final Instant T0 = Instant.parse("2020-01-01T00:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final IntegrationMetrics metrics = new IntegrationMetrics(registry);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private final Logger metricsLogger = (Logger) LoggerFactory.getLogger(IntegrationMetrics.class);

    private final SteppableClock clock = new SteppableClock(T0);

    private MockRestServiceServer server;
    private EntraPhotoSource sut;

    /**
     * Built on the client {@code EntraIdpConfig} builds, so the token, the bearer header and the expected-status marker
     * all run through the real interceptors. One mock server answers both the token endpoint and Graph, in the order a
     * real call reaches them.
     */
    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        logs.start();
        metricsLogger.addAppender(logs);

        sut = EntraGraph.photoSource(builder, metrics, clock);
    }

    @AfterEach
    void tearDown() {
        metricsLogger.detachAppender(logs);
        logs.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    void theTokenRequestIsAClientCredentialsGrantForGraphAtTheTenantsOwnEndpoint() {

        // given
        server.expect(requestTo(EntraGraph.tokenEndpoint(TENANT))).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=app-client-id")))
                .andExpect(content().string(containsString("client_secret=app-secret")))
                .andExpect(content().string(containsString("scope=https%3A%2F%2Fgraph.microsoft.com%2F.default")))
                .andRespond(withSuccess(EntraGraph.tokenJson("graph-token"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(METADATA_ENDPOINT)).andExpect(header("Authorization", "Bearer graph-token"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // when
        var result = sut.photoMetadata("entra-1", TENANT);

        // then
        server.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void photoMetadata_returnsEtagWithBearerToken() {

        // given
        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(METADATA_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer graph-token")).andRespond(withSuccess(
                        "{\"@odata.mediaEtag\": \"W/\\\"etag-1\\\"\", \"width\": 240}", MediaType.APPLICATION_JSON));

        // when
        var result = sut.photoMetadata("entra-1", TENANT);

        // then
        server.verify();
        assertThat(result).contains(new EntraPhotoSource.PhotoMetadata("W/\"etag-1\""));
    }

    @Test
    void photoMetadata_notFound_returnsEmpty() {

        // given - Graph answers 404 when the user has no photo
        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(METADATA_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // when / then
        assertThat(sut.photoMetadata("entra-1", TENANT)).isEmpty();
    }

    @Test
    void fetchPhoto_returnsBytesAndContentType() {

        // given
        EntraGraph.expectToken(server, TENANT, "graph-token");
        var bytes = new byte[]{1, 2, 3};
        server.expect(requestTo(BYTES_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer graph-token"))
                .andRespond(withSuccess(bytes, MediaType.IMAGE_JPEG));

        // when
        var result = sut.fetchPhoto("entra-1", TENANT);

        // then
        server.verify();
        assertThat(result).isPresent();
        assertThat(result.get().bytes()).containsExactly(1, 2, 3);
        assertThat(result.get().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void fetchPhoto_notFound_returnsEmpty() {

        // given
        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(BYTES_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // when / then
        assertThat(sut.fetchPhoto("entra-1", TENANT)).isEmpty();
    }

    @Test
    void fetchPhoto_missingContentType_defaultsToJpeg() {

        // given - a 200 with bytes but no Content-Type header
        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(BYTES_ENDPOINT)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{9}, null));

        // when
        var result = sut.fetchPhoto("entra-1", TENANT);

        // then
        assertThat(result.orElseThrow().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void fetchPhoto_emptyBody_returnsEmpty() {

        // given - defensive: a 200 with no body is useless for storage
        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(BYTES_ENDPOINT)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess());

        // when / then
        assertThat(sut.fetchPhoto("entra-1", TENANT)).isEmpty();
    }

    @Test
    void photoMetadata_twoTenants_readsEachFromItsOwnTenantToken() {

        // given - two Entra customers, two tokens from two token endpoints; the same Graph host serves both
        EntraGraph.expectToken(server, "tenant-acme", "token-acme");
        server.expect(requestTo(GRAPH_BASE + "/v1.0/users/acme-user/photo"))
                .andExpect(header("Authorization", "Bearer token-acme"))
                .andRespond(withSuccess("{\"@odata.mediaEtag\": \"acme-etag\"}", MediaType.APPLICATION_JSON));
        EntraGraph.expectToken(server, "tenant-globex", "token-globex");
        server.expect(requestTo(GRAPH_BASE + "/v1.0/users/globex-user/photo"))
                .andExpect(header("Authorization", "Bearer token-globex"))
                .andRespond(withSuccess("{\"@odata.mediaEtag\": \"globex-etag\"}", MediaType.APPLICATION_JSON));

        // when
        var acme = sut.photoMetadata("acme-user", "tenant-acme");
        var globex = sut.photoMetadata("globex-user", "tenant-globex");

        // then - neither read carried the other customer's bearer token
        server.verify();
        assertThat(acme).contains(new EntraPhotoSource.PhotoMetadata("acme-etag"));
        assertThat(globex).contains(new EntraPhotoSource.PhotoMetadata("globex-etag"));
    }

    @Test
    void fetchPhoto_twoTenants_downloadsEachWithItsOwnTenantToken() {

        // given
        EntraGraph.expectToken(server, "tenant-acme", "token-acme");
        server.expect(requestTo(GRAPH_BASE + "/v1.0/users/acme-user/photos/240x240/$value"))
                .andExpect(header("Authorization", "Bearer token-acme"))
                .andRespond(withSuccess(new byte[]{1}, MediaType.IMAGE_JPEG));
        EntraGraph.expectToken(server, "tenant-globex", "token-globex");
        server.expect(requestTo(GRAPH_BASE + "/v1.0/users/globex-user/photos/240x240/$value"))
                .andExpect(header("Authorization", "Bearer token-globex"))
                .andRespond(withSuccess(new byte[]{2}, MediaType.IMAGE_JPEG));

        // when
        var acme = sut.fetchPhoto("acme-user", "tenant-acme");
        var globex = sut.fetchPhoto("globex-user", "tenant-globex");

        // then
        server.verify();
        assertThat(acme.orElseThrow().bytes()).containsExactly(1);
        assertThat(globex.orElseThrow().bytes()).containsExactly(2);
    }

    @Test
    void oneTokenServesCallsMadeOnBehalfOfTwoSignedInPeople() {

        // given - one token request only
        EntraGraph.expectToken(server, TENANT, "graph-token");
        expectMetadata("entra-1", "graph-token");
        expectMetadata("entra-1", "graph-token");

        // when
        signIn("ada");
        sut.photoMetadata("entra-1", TENANT);
        signIn("grace");
        sut.photoMetadata("entra-1", TENANT);

        // then
        server.verify();
    }

    @Test
    void a401DropsTheTokenSoTheNextCallMintsAFreshOne() {

        // given
        EntraGraph.expectToken(server, TENANT, "tok-1");
        server.expect(requestTo(METADATA_ENDPOINT)).andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        EntraGraph.expectToken(server, TENANT, "tok-2");
        expectMetadata("entra-1", "tok-2");

        // when / then - the call that met the 401 still fails; the next one carries a fresh token
        assertThatThrownBy(() -> sut.photoMetadata("entra-1", TENANT))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(sut.photoMetadata("entra-1", TENANT)).isPresent();
        server.verify();
    }

    @Test
    void theTokenIsRenewedOnlyOnceTheClockPassesFiveMinutesBeforeItExpires() {

        // given - an hour-long token: kept at exactly 55 minutes, renewed one nanosecond later
        EntraGraph.expectToken(server, TENANT, "tok-1");
        expectMetadata("entra-1", "tok-1");
        expectMetadata("entra-1", "tok-1");
        EntraGraph.expectToken(server, TENANT, "tok-2");
        expectMetadata("entra-1", "tok-2");

        // when
        sut.photoMetadata("entra-1", TENANT);
        clock.advance(Duration.ofMinutes(55));
        sut.photoMetadata("entra-1", TENANT);
        clock.advance(Duration.ofNanos(1));
        sut.photoMetadata("entra-1", TENANT);

        // then
        server.verify();
    }

    /**
     * Most members of a large directory never set a Graph photo, and this call runs once per member per sync. Counted
     * as a failure it would put a permanent floor under {@code entra}'s failure rate and emit thousands of WARN lines
     * per tick, which is a log bill and an operator who learns to scroll past {@code integration=entra}.
     *
     * <p>
     * An {@code onStatus} handler cannot achieve this: the interceptor runs below {@code retrieve()} and sees the raw
     * 404 whatever the call site does with it afterwards. The marker is what tells it.
     */
    @Test
    void photoMetadata_notFound_isCountedAsAnAnsweredCallAndLogsNothing() {

        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(METADATA_ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(sut.photoMetadata("entra-1", TENANT)).isEmpty();

        assertThat(calls("success")).isEqualTo(2.0);
        assertThat(calls("failure")).isZero();
        assertThat(logs.list).isEmpty();
    }

    @Test
    void fetchPhoto_notFound_isCountedAsAnAnsweredCallAndLogsNothing() {

        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(BYTES_ENDPOINT)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(sut.fetchPhoto("entra-1", TENANT)).isEmpty();

        assertThat(calls("success")).isEqualTo(2.0);
        assertThat(calls("failure")).isZero();
        assertThat(logs.list).isEmpty();
    }

    /**
     * The marker names one status, so it exempts nothing else. A 403 is Graph refusing the read - a missing
     * {@code ProfilePhoto.Read.All} consent - and must still count and still be logged.
     */
    @Test
    void photoMetadata_forbidden_isStillCountedAsAFailedCallAndLogged() {

        EntraGraph.expectToken(server, TENANT, "graph-token");
        server.expect(requestTo(METADATA_ENDPOINT)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> sut.photoMetadata("entra-1", TENANT)).isInstanceOf(HttpClientErrorException.class);

        assertThat(calls("success")).isEqualTo(1.0);
        assertThat(calls("failure")).isEqualTo(1.0);
        assertThat(logs.list).hasSize(1);
    }

    private void expectMetadata(String user, String token) {
        server.expect(requestTo(GRAPH_BASE + "/v1.0/users/" + user + "/photo")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess("{\"@odata.mediaEtag\": \"etag\"}", MediaType.APPLICATION_JSON));
    }

    private static void signIn(String name) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(name,
                null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private double calls(String outcome) {

        return registry.get("axonia.integration.calls").tag("integration", "entra").tag("outcome", outcome).counter()
                .count();
    }
}
