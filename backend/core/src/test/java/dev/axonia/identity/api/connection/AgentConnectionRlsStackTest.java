package dev.axonia.identity.api.connection;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.DedicatedPostgresContainer;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.tenancy.OrganizationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@CoreSpringBootTest
@ActiveProfiles("DEV")
@Testcontainers
class AgentConnectionRlsStackTest {

    private static final String CLIENT = "https://claude.ai/oauth/claude-code-client-metadata";
    private static final String REDIRECT = "http://localhost:3118/callback";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Container
    static final DedicatedPostgresContainer PG = new DedicatedPostgresContainer();

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {

        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", PG::getUsername);
        registry.add("spring.flyway.password", PG::getPassword);
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "axonia_app");
        registry.add("spring.datasource.password", () -> "axonia_app");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    AgentConnections connections;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theConnectionIsTheLeastPrivilegedRole() {
        assertThat(jdbc.queryForObject("SELECT current_user", Map.of(), String.class)).isEqualTo("axonia_app");
    }

    @Test
    void theWholeLifecycleWorksWithNoOrganizationInTheContextExceptConsent() {

        // given: consent runs with the member's own token
        actAs("u-1", "acme");
        var code = connections.authorize(authorize("u-1", "acme"));
        SecurityContextHolder.clearContext();

        // when: the token endpoint and the /mcp filter run with no organization at all
        var issued = connections.redeem(new RedeemCode(code, CLIENT, REDIRECT, VERIFIER)).orElseThrow();
        var principal = connections.authenticate(issued.accessToken()).orElseThrow();
        var rotated = connections.refresh(new RefreshConnection(issued.refreshToken(), CLIENT)).orElseThrow();

        // then
        assertThat(principal.organizationId()).isEqualTo("acme");
        assertThat(principal.userId()).isEqualTo("u-1");
        assertThat(connections.authenticate(issued.accessToken())).as("the old access token died with the refresh")
                .isEmpty();
        assertThat(connections.authenticate(rotated.accessToken())).isPresent();

        connections.revokeToken(rotated.refreshToken());
        assertThat(connections.authenticate(rotated.accessToken())).isEmpty();
    }

    @Test
    void aMemberSeesOnlyTheirOwnOrganizationsConnections() {

        // given
        actAs("u-2", "acme");
        var acmeCode = connections.authorize(authorize("u-2", "acme"));
        SecurityContextHolder.clearContext();
        connections.redeem(new RedeemCode(acmeCode, CLIENT, REDIRECT, VERIFIER)).orElseThrow();

        // when
        actAs("u-2", "default");
        List<?> actual = connections.list("u-2", "acme");

        // then
        assertThat(actual).isEmpty();
    }

    // Member revoke, usage, sweep and code replay, each as axonia_app

    @Test
    void aMemberCannotRevokeAColleaguesConnection_butTheOwnerCan() {

        // given
        var issued = connect("u-3");
        var connectionId = connections.authenticate(issued.accessToken()).orElseThrow().connectionId();

        // when
        actAs("u-4", "acme");
        var colleague = catchThrowable(() -> connections.revoke("u-4", "acme", connectionId));
        SecurityContextHolder.clearContext();
        var stillLive = connections.authenticate(issued.accessToken());
        actAs("u-3", "acme");
        connections.revoke("u-3", "acme", connectionId);
        SecurityContextHolder.clearContext();

        // then
        assertThat(colleague).isInstanceOf(NotFoundException.class);
        assertThat(stillLive).isPresent();
        assertThat(connections.authenticate(issued.accessToken())).isEmpty();
    }

    @Test
    void recordingUsageMovesLastUsedAt() {

        // given
        var issued = connect("u-5");
        var connectionId = connections.authenticate(issued.accessToken()).orElseThrow().connectionId();

        // when: as the /mcp filter does, under the synthesized member context
        actAs("u-5", "acme");
        connections.recordUsage("acme", connectionId);

        // then
        assertThat(connections.list("u-5", "acme")).singleElement()
                .satisfies(connection -> assertThat(connection.lastUsedAt()).isNotNull());
    }

    @Test
    void theSweepDeletesExpiredTokensAndLongRetiredConnections() {

        // given: a live connection whose code and access token expired, and a connection revoked long ago
        var live = connections.authenticate(connect("u-6").accessToken()).orElseThrow().connectionId();
        var retired = connections.authenticate(connect("u-7").accessToken()).orElseThrow().connectionId();
        OrganizationScope.runAs("acme", () -> {
            jdbc.update("UPDATE agent_connection_token SET expires_at = now() - interval '1 minute' "
                    + "WHERE connection_id = :id AND kind <> 'REFRESH'", Map.of("id", live));
            jdbc.update("UPDATE agent_connection SET revoked_at = now() - interval '31 days' WHERE id = :id",
                    Map.of("id", retired));
        });

        // when
        var swept = OrganizationScope.runAs("acme", () -> connections.sweep("acme"));

        // then
        assertThat(swept).isGreaterThanOrEqualTo(2);
        assertThat(tokenKinds(live)).containsExactly("REFRESH");
        assertThat(tokenKinds(retired)).isEmpty();
        assertThat(OrganizationScope.runAs("acme",
                () -> jdbc.queryForList("SELECT id FROM agent_connection WHERE id IN (:ids)",
                        Map.of("ids", List.of(live, retired)), Long.class)))
                .containsExactly(live);
    }

    @Test
    void replayingACodeRevokesTheConnectionItActivated() {

        // given
        actAs("u-8", "acme");
        var code = connections.authorize(authorize("u-8", "acme"));
        SecurityContextHolder.clearContext();
        var issued = connections.redeem(new RedeemCode(code, CLIENT, REDIRECT, VERIFIER)).orElseThrow();

        // when
        var replay = connections.redeem(new RedeemCode(code, CLIENT, REDIRECT, VERIFIER));

        // then
        assertThat(replay).isEmpty();
        assertThat(connections.authenticate(issued.accessToken())).isEmpty();
        actAs("u-8", "acme");
        assertThat(connections.list("u-8", "acme")).singleElement()
                .satisfies(connection -> assertThat(connection.status()).isEqualTo("REVOKED"));
    }

    private IssuedTokens connect(String userId) {

        actAs(userId, "acme");
        var code = connections.authorize(authorize(userId, "acme"));
        SecurityContextHolder.clearContext();
        return connections.redeem(new RedeemCode(code, CLIENT, REDIRECT, VERIFIER)).orElseThrow();
    }

    private List<String> tokenKinds(Long connectionId) {

        return jdbc.queryForList("SELECT kind FROM agent_connection_token WHERE connection_id = :id",
                Map.of("id", connectionId), String.class);
    }

    private static AuthorizeConnection authorize(String userId, String organizationId) {

        return AuthorizeConnection.builder().userId(userId).organizationId(organizationId).clientId(CLIENT)
                .clientName("Claude Code").clientHost("claude.ai").scopes(Set.of("read"))
                .resource("https://axonia.test/mcp").redirectUri(REDIRECT).codeChallenge(CHALLENGE).build();
    }

    private static void actAs(String subject, String organization) {

        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).claim("org", organization).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
