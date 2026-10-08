package dev.axonia.identity.scim;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for the SCIM security filter chain ({@code /scim/v2/**}).
 *
 * <p>
 * Boots the full Spring application context against a Testcontainers-managed Postgres (via the {@code jdbc:tc:} URL in
 * {@code application-DEV.properties}) and drives real HTTP requests to verify:
 * <ul>
 * <li>A valid SCIM bearer token passes authentication (result is NOT 401/403).</li>
 * <li>A bad token returns a SCIM-shaped 401.</li>
 * <li>A missing Authorization header returns a SCIM-shaped 401.</li>
 * <li>A SCIM token presented to the JWT chain ({@code /axonia/**}) is rejected with 401 (chains don't bleed).</li>
 * </ul>
 *
 * <p>
 * Since the SCIM Users/Groups controllers land in later tasks, routes under {@code /scim/v2/**} return 404 when
 * authentication succeeds. The test exploits this: a 404 proves auth passed; a 401 proves auth failed.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ScimSecurityChainTest {

    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);
    private static volatile String PLAINTEXT;

    @LocalServerPort
    private int port;

    @Autowired
    private ScimTokens scimTokens;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Stand in for the resource-server JwtDecoder so the JWT chain never reaches the (unreachable) issuer. Stubbed to
     * reject any presented token as a malformed JWT - exactly how the real Nimbus decoder behaves in production when
     * handed a non-JWT SCIM bearer against a reachable issuer.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seedToken() {
        // @MockitoBean is reset between tests, so (re)stub on every test: a SCIM bearer is not a valid JWT.
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("not a JWT"));

        if (SEEDED.compareAndSet(false, true)) {
            // The lifecycle gate now requires the Organization to exist in the registry, same as the JWT chain.
            ensureOrganization("default");
            // SCIM tokens are scoped to the Organization boundary; no sub-org id.
            PLAINTEXT = scimTokens.generate("default", "chain-test", null);
        }
    }

    /** The lifecycle gate 401s any Organization absent from the registry; this test's tokens must resolve one. */
    private void ensureOrganization(String organizationId) {
        var exists = jdbc.queryForList("SELECT id FROM organization WHERE id = ?", organizationId);
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES (?, ?, 'ENTERPRISE', now(), now())", organizationId, organizationId);
        }
    }

    // -------------------------------------------------------------------------
    // Happy path: valid token → auth passes, controller absent → 404 (not 401/403)
    // -------------------------------------------------------------------------

    @Test
    void validToken_passesScimAuth_andIsNotRejected() throws Exception {

        // when
        var response = get("/scim/v2/Users", PLAINTEXT);

        // then
        // 404 = no controller registered yet; anything but 401/403 confirms auth was accepted
        assertThat(response.statusCode()).as("valid SCIM token must not be rejected by the auth filter")
                .isNotEqualTo(401).isNotEqualTo(403);
    }

    // -------------------------------------------------------------------------
    // Bad token: SCIM-shaped 401
    // -------------------------------------------------------------------------

    @Test
    void badToken_returns401WithScimErrorBody() throws Exception {

        // when
        var response = get("/scim/v2/Users", "scim_not-a-real-token");

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).as("error body must carry the SCIM Error URN")
                .contains("urn:ietf:params:scim:api:messages:2.0:Error");
    }

    // -------------------------------------------------------------------------
    // Missing Authorization: SCIM-shaped 401
    // -------------------------------------------------------------------------

    @Test
    void missingAuthorizationHeader_returns401WithScimErrorBody() throws Exception {

        // when
        var response = getNoAuth("/scim/v2/Users");

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).as("error body must carry the SCIM Error URN")
                .contains("urn:ietf:params:scim:api:messages:2.0:Error");
    }

    // -------------------------------------------------------------------------
    // Chains don't bleed: SCIM token NOT accepted by the JWT chain
    // -------------------------------------------------------------------------

    @Test
    void scimToken_isRejectedByJwtChain() throws Exception {

        // when
        // /axonia/** is guarded by the JWT chain (@Order(2)).
        // A SCIM bearer string is not a valid JWT → the JWT decoder rejects it → 401.
        var response = get("/axonia/access/me", PLAINTEXT);

        // then
        assertThat(response.statusCode()).as("SCIM bearer token must not authenticate the JWT-gated /axonia chain")
                .isEqualTo(401);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HttpResponse<String> get(String path, String bearerToken) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", "Bearer " + bearerToken).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getNoAuth(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
