package dev.axonia.agent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.DedicatedPostgresContainer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * The MCP OAuth flow through the running app: consent, the redirect, the token exchange, a tool call, refresh rotation,
 * revocation and Disconnect, each over real HTTP. Only the Client ID Metadata Document fetch is stubbed.
 *
 * <p>
 * The app connects as {@code axonia_app}, not the schema owner, the way {@link dev.axonia.RlsStackIsolationTest} does:
 * a token is found by its digest before any organization is known and every later step runs in that token's scope, and
 * an owner connection would bypass row-level security and prove none of it. Fixtures are seeded, and the audit is read,
 * as the owner.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("DEV")
@Testcontainers
class AgentOAuthEndToEndTest {

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    private static final String ORG = "oauth-e2e";
    private static final String USER = "oauth-e2e-user";
    private static final String MEMBER_JWT = "member-jwt";
    private static final String ISSUER = "https://dev-local.example.com";
    private static final String RESOURCE = ISSUER + "/mcp";
    private static final String CHALLENGE_METADATA = "resource_metadata=\"" + ISSUER
            + "/.well-known/oauth-protected-resource/mcp\"";
    private static final String CLIENT = "https://claude.ai/oauth/claude-code-client-metadata";
    private static final String REDIRECT = "http://localhost:54321/callback";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {

        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("axonia.tenant-discovery.datasource.url", PG::getJdbcUrl);
        registry.add("axonia.tenant-discovery.datasource.username", () -> "axonia_communication_read");
        registry.add("axonia.tenant-discovery.datasource.password", () -> "communication_read");
        registry.add("axonia.tenant-discovery.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Pinned rather than left to whatever axonia.public-base-url defaults to: the default has moved once
        // already, and this suite's own ISSUER, RESOURCE and CHALLENGE_METADATA constants are built
        // from it, not read back from the running app.
        registry.add("axonia.public-base-url", () -> ISSUER);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate appJdbc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientMetadataDocuments documents;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seed() throws Exception {

        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("not a JWT"));
        doReturn(Jwt.withTokenValue(MEMBER_JWT).header("alg", "RS256").subject(USER).claim("org", ORG)
                .claim("groups", List.of()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build())
                .when(jwtDecoder).decode(MEMBER_JWT);
        when(documents.resolve(CLIENT)).thenReturn(Optional.of(new ClientMetadata(CLIENT, "Claude Code", "claude.ai",
                List.of("http://localhost/callback", "http://127.0.0.1/callback"))));
        if (SEEDED.compareAndSet(false, true)) {
            asOwner("INSERT INTO organization (id, name, kind, created_at, updated_at) "
                    + "VALUES (?, 'OAuth Enterprise', 'ENTERPRISE', now(), now())", ORG);
            asOwner("INSERT INTO identity_user (organization_id, user_id, display_name, active, scim_id, arrival, "
                    + "created_at, updated_at, version) VALUES (?, ?, 'OAuth Member', true, ?, 'SYNCED', now(), now(), 0)",
                    ORG, USER, "scim-" + USER);
        }
    }

    @Test
    void theAppRunsAsTheLeastPrivilegedRoleNotTheSchemaOwner() {

        assertThat(appJdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("axonia_app");
    }

    @Test
    void anUnauthenticatedCallLeadsTheClientToTheMetadata() throws Exception {

        // when
        var challenge = http.send(mcpPost(null), HttpResponse.BodyHandlers.ofString());
        var resource = http.send(get("/.well-known/oauth-protected-resource/mcp"),
                HttpResponse.BodyHandlers.ofString());
        var server = http.send(get("/.well-known/oauth-authorization-server"), HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(challenge.statusCode()).isEqualTo(401);
        assertThat(challenge.headers().firstValue("WWW-Authenticate")).contains("Bearer " + CHALLENGE_METADATA);
        assertThat(JSON.readTree(resource.body()).path("resource").asString()).isEqualTo(RESOURCE);
        assertThat(JSON.readTree(resource.body()).path("authorization_servers").get(0).asString()).isEqualTo(ISSUER);
        assertThat(JSON.readTree(server.body()).path("token_endpoint").asString()).isEqualTo(ISSUER + "/oauth/token");
    }

    @Test
    void consentRedirectsWithCodeStateAndIss_theCodeBuysTokens_andAReplayRevokesTheConnection() throws Exception {

        // consent
        var review = postJson("/axonia/me/agent-connections/authorization-requests", authorizeRequest());
        assertThat(review.path("outcome").asString()).isEqualTo("READY");
        assertThat(review.path("clientName").asString()).isEqualTo("Claude Code");
        assertThat(review.path("clientHost").asString()).isEqualTo("claude.ai");
        var approval = postJson("/axonia/me/agent-connections/authorization-requests/approve", approval());
        assertThat(approval.path("outcome").asString()).isEqualTo("REDIRECT");
        var redirect = URI.create(approval.path("redirectUrl").asString());
        assertThat(redirect.getScheme() + "://" + redirect.getAuthority() + redirect.getPath()).isEqualTo(REDIRECT);
        assertThat(queryParam(redirect, "state")).isEqualTo("s-1");
        assertThat(queryParam(redirect, "iss")).isEqualTo(ISSUER);
        var code = queryParam(redirect, "code");
        assertThat(code).startsWith("axc_");

        // token
        var issued = tokenRequest(codeGrant(code) + "&resource=" + enc(RESOURCE));
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(200);
        assertThat(issued.headers().firstValue("Cache-Control")).contains("no-store");
        var tokens = JSON.readTree(issued.body());
        assertThat(tokens.path("token_type").asString()).isEqualTo("Bearer");
        assertThat(tokens.path("scope").asString()).isEqualTo("read");
        assertThat(tokens.path("access_token").asString()).startsWith("axa_");
        assertThat(tokens.path("refresh_token").asString()).startsWith("axr_");
        var accessToken = tokens.path("access_token").asString();
        assertToolCallSucceeds(accessToken);

        // the code is single use
        var replay = tokenRequest(codeGrant(code));
        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(replay.body()).path("error").asString()).isEqualTo("invalid_grant");

        // and the replay revoked the connection it created (RFC 6749 section 4.1.2)
        assertRefused(accessToken);
    }

    @Test
    void aConnectionTokenCallsATool_refreshes_andIsRevoked() throws Exception {

        // given
        var tokens = connectOnce();
        assertToolCallSucceeds(tokens.path("access_token").asString());
        var connectionId = activeConnection().path("id").asLong();
        assertThat(activeConnection().hasNonNull("lastUsedAt")).isTrue();

        // when: refresh rotates
        var refreshed = tokenRequest("grant_type=refresh_token&refresh_token=" + tokens.path("refresh_token").asString()
                + "&client_id=" + enc(CLIENT));
        assertThat(refreshed.statusCode()).as(refreshed.body()).isEqualTo(200);
        var rotated = JSON.readTree(refreshed.body());

        // then: the old access token died, the new one works
        assertRefused(tokens.path("access_token").asString());
        assertToolCallSucceeds(rotated.path("access_token").asString());

        // when: the client revokes its refresh token
        var revoked = http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/oauth/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("token=" + rotated.path("refresh_token").asString())).build(),
                HttpResponse.BodyHandlers.ofString());

        // then: the whole connection ended, access token included
        assertThat(revoked.statusCode()).isEqualTo(200);
        assertRefused(rotated.path("access_token").asString());
        assertThat(connection(connectionId).path("status").asString()).isEqualTo("REVOKED");
    }

    @Test
    void aToolCallOverAConnectionIsAuditedUnderThatConnection() throws Exception {

        // given
        var tokens = connectOnce();
        var connectionId = activeConnection().path("id").asLong();

        // when
        assertToolCallSucceeds(tokens.path("access_token").asString());

        // then
        assertThat(ownerCount("SELECT count(*) FROM agent_tool_call WHERE organization_id = ? AND user_id = ? "
                + "AND connection_id = ? AND token_id IS NULL AND tool_name = 'communities.list' AND outcome = 'OK'",
                ORG, USER, connectionId)).isEqualTo(1);
    }

    @Test
    void disconnectingFromTheAppEndsTheConnection() throws Exception {

        // given
        var tokens = connectOnce();
        var connectionId = activeConnection().path("id").asLong();

        // when
        var disconnected = http.send(
                authed(HttpRequest.newBuilder(URI.create(baseUrl() + "/axonia/me/agent-connections/" + connectionId)))
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(disconnected.statusCode()).isEqualTo(204);
        assertRefused(tokens.path("access_token").asString());
        var refresh = tokenRequest("grant_type=refresh_token&refresh_token=" + tokens.path("refresh_token").asString()
                + "&client_id=" + enc(CLIENT));
        assertThat(refresh.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(refresh.body()).path("error").asString()).isEqualTo("invalid_grant");
    }

    @Test
    void approvingAgainReplacesThePreviousConnection() throws Exception {

        // when
        var first = connectOnce();
        var second = connectOnce();

        // then
        assertRefused(first.path("access_token").asString());
        assertToolCallSucceeds(second.path("access_token").asString());
        var active = connections().valueStream().filter(c -> "ACTIVE".equals(c.path("status").asString())).toList();
        assertThat(active).hasSize(1);
    }

    private JsonNode connectOnce() throws Exception {

        var approval = postJson("/axonia/me/agent-connections/authorization-requests/approve", approval());
        var code = queryParam(URI.create(approval.path("redirectUrl").asString()), "code");
        var issued = tokenRequest(codeGrant(code));
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(200);
        return JSON.readTree(issued.body());
    }

    /** A real MCP session: initialize, then a tool call, over the streamable transport. */
    private void assertToolCallSucceeds(String accessToken) {

        var client = connect(accessToken);
        try {
            client.initialize();
            var result = client.callTool(new McpSchema.CallToolRequest("communities.list", Map.of()));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        } finally {
            close(client);
        }
    }

    /** RFC 9728 section 5.1: every refusal of a presented bearer names where to authorize again. */
    private void assertRefused(String accessToken) throws Exception {

        var response = http.send(mcpPost(accessToken), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .contains("Bearer error=\"invalid_token\", " + CHALLENGE_METADATA);
    }

    private JsonNode activeConnection() throws Exception {

        return connections().valueStream().filter(c -> "ACTIVE".equals(c.path("status").asString())).findFirst()
                .orElseThrow();
    }

    private JsonNode connection(long id) throws Exception {

        return connections().valueStream().filter(c -> c.path("id").asLong() == id).findFirst().orElseThrow();
    }

    private JsonNode connections() throws Exception {

        var listed = http.send(
                authed(HttpRequest.newBuilder(URI.create(baseUrl() + "/axonia/me/agent-connections"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
        return JSON.readTree(listed.body());
    }

    private static String authorizeRequest() {

        return "{\"response_type\":\"code\",\"client_id\":\"" + CLIENT + "\",\"redirect_uri\":\"" + REDIRECT
                + "\",\"code_challenge\":\"" + CHALLENGE + "\",\"code_challenge_method\":\"S256\"," + "\"resource\":\""
                + RESOURCE + "\",\"scope\":\"read\",\"state\":\"s-1\"}";
    }

    private static String approval() {
        return "{\"request\":" + authorizeRequest() + ",\"scopes\":[\"read\"],\"organizationId\":\"" + ORG + "\"}";
    }

    private static String codeGrant(String code) {

        return "grant_type=authorization_code&code=" + code + "&client_id=" + enc(CLIENT) + "&redirect_uri="
                + enc(REDIRECT) + "&code_verifier=" + VERIFIER;
    }

    private static String queryParam(URI uri, String name) {

        var raw = UriComponentsBuilder.fromUri(uri).build(true).getQueryParams().getFirst(name);
        return raw == null ? null : UriUtils.decode(raw, StandardCharsets.UTF_8);
    }

    private JsonNode postJson(String path, String body) throws Exception {

        var response = http.send(authed(HttpRequest.newBuilder(URI.create(baseUrl() + path)))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> tokenRequest(String form) throws Exception {

        return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest mcpPost(String bearer) {

        var builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp")).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream").POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return builder.build();
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build();
    }

    private HttpRequest.Builder authed(HttpRequest.Builder builder) {
        return builder.header("Authorization", "Bearer " + MEMBER_JWT);
    }

    private McpSyncClient connect(String bearer) {

        var transport = HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/mcp")
                .httpRequestCustomizer(
                        (builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + bearer))
                .build();
        return McpClient.sync(transport).clientInfo(new McpSchema.Implementation("axonia-oauth-e2e", "1.0.0")).build();
    }

    private static void close(McpSyncClient client) {
        client.closeGracefully();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void asOwner(String sql, Object... args) throws Exception {

        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var ps = c.prepareStatement(sql)) {
            for (var i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.execute();
        }
    }

    private static long ownerCount(String sql, Object... args) throws Exception {

        try (var c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                var ps = c.prepareStatement(sql)) {
            for (var i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
