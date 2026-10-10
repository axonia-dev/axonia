package dev.axonia.selfhosted;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import dev.axonia.access.api.Permission;
import dev.axonia.installation.application.MailServerPermissions;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signing in to the Self-hosted application with real tokens of a real issuer, through its real port: the decoder, the
 * converter, the founding and the open modules behind them, as a browser behind {@code selfhosted-edge} meets them.
 * Every test signs in as a subject of its own.
 */
@SelfHostedSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SelfHostedSignInTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private MockOidcIssuer issuer;

    @Test
    void signsInWithARealTokenAndIsProvisionedJustInTime() throws Exception {

        // given
        var subject = unique("alice");
        var token = issuer.token(subject, Map.of("email", subject + "@acme.test", "name", "Alice Example"));

        // when
        var actual = get("/axonia/me", token);

        // then
        assertThat(actual.statusCode()).isEqualTo(200);
        var me = JSON.readTree(actual.body());
        assertThat(me.path("displayName").asString()).isEqualTo("Alice Example");
        assertThat(me.path("email").asString()).isEqualTo(subject + "@acme.test");
        assertThat(profilesOf(subject, SelfHostedTestInfrastructure.ORGANIZATION_ID)).isOne();
    }

    @Test
    void orgIsTheConfiguredOrganizationWhateverTheTokenSays() throws Exception {

        // given
        var subject = unique("mallory");

        // when
        var actual = get("/axonia/me", issuer.token(subject, Map.of("org", "evil")));

        // then
        assertThat(actual.statusCode()).isEqualTo(200);
        assertThat(profilesOf(subject, SelfHostedTestInfrastructure.ORGANIZATION_ID)).isOne();
        assertThat(profilesOf(subject, "evil")).isZero();
    }

    @Test
    void refusesATokenForAnotherAudience() throws Exception {

        assertThat(get("/axonia/me", issuer.token(unique("eve"), Map.of("aud", "another-application"))).statusCode())
                .isEqualTo(401);
    }

    @Test
    void refusesATokenWithNoAudience() throws Exception {

        assertThat(get("/axonia/me", issuer.token(unique("eve"), Map.of("aud", List.of()))).statusCode())
                .isEqualTo(401);
    }

    @Test
    void refusesATokenFromAnotherIssuer() throws Exception {

        assertThat(get("/axonia/me", issuer.foreignToken(unique("eve"), Map.of())).statusCode()).isEqualTo(401);
    }

    @Test
    void refusesATokenWithNoSubjectClaim() throws Exception {

        assertThat(get("/axonia/me", issuer.token(null, Map.of("oid", "o-1"))).statusCode()).isEqualTo(401);
    }

    /** A token that says somebody acts for the person is not the person's own sign-in, so it is refused outright. */
    @Test
    void refusesATokenSomebodyElseActsWith() throws Exception {

        // when
        var actual = get("/axonia/me", issuer.token(unique("alice"), Map.of("act", Map.of("sub", "service-1"))));

        // then
        assertThat(actual.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(actual.body()).path("code").asString()).isEqualTo("invalid_token");
    }

    @Test
    void acceptsATokenWhoseUnrelatedClaimMerelyResemblesTheActorClaim() throws Exception {

        assertThat(get("/axonia/me", issuer.token(unique("alice"), Map.of("actor", Map.of("sub", "service-1"))))
                .statusCode()).isEqualTo(200);
    }

    @Test
    void aMemberOfTheOwnerGroupIsOwnerOnFirstSignIn() throws Exception {

        // given
        var token = owner("owner");
        var kinds = JSON.readTree(get("/axonia/community-kinds", token).body());

        // when
        var created = post("/axonia/communities", token,
                Map.of("name", unique("Owners community"), "kindId", kinds.get(0).path("id").asLong()));
        var access = get("/axonia/access/me", token);

        // then
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(access.statusCode()).isEqualTo(200);
        assertThat(List.of(JSON.treeToValue(JSON.readTree(access.body()).path("permissions"), String[].class)))
                .containsExactlyElementsOf(Stream
                        .concat(Permission.OPEN.stream(),
                                Stream.of(MailServerPermissions.MAIL_SERVER_READ,
                                        MailServerPermissions.MAIL_SERVER_WRITE))
                        .map(Permission::code).sorted().toList());
    }

    @Test
    void aMemberOutsideTheOwnerGroupIsNotOwner() throws Exception {

        // given
        var token = issuer.token(unique("member"), Map.of("groups", List.of("engineering")));
        var kinds = JSON.readTree(get("/axonia/community-kinds", owner("kinds-reader")).body());

        // when
        var refused = post("/axonia/communities", token,
                Map.of("name", unique("Members community"), "kindId", kinds.get(0).path("id").asLong()));

        // then
        assertThat(refused.statusCode()).isEqualTo(403);
    }

    @Test
    void aTokenInGroupOverageSignsInWithNoGroup() throws Exception {

        // given
        var token = issuer.token(unique("overage"), Map.of("_claim_names", Map.of("groups", "src1")));

        // when
        var me = get("/axonia/me", token);
        var permissions = JSON.readTree(get("/axonia/access/me", token).body()).path("permissions");

        // then
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(permissions.isArray()).isTrue();
        assertThat(permissions.size()).isZero();
    }

    @Test
    void aPersonalAccessTokenReachesTheAgentSurfaceAndNotTheRestOne() throws Exception {

        // given
        var minted = post("/axonia/me/tokens", owner("agent-owner"),
                Map.of("name", "probe", "scopes", List.of("read")));
        assertThat(minted.statusCode()).isEqualTo(201);
        var personal = JSON.readTree(minted.body()).path("token").asString();

        // when
        var toolsList = http.send(
                request("/mcp", personal).header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(TOOLS_LIST)).build(),
                HttpResponse.BodyHandlers.ofString());
        var rest = get("/axonia/me", personal);

        // then
        assertThat(toolsList.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(toolsList.body()).path("message").asString()).isEqualTo("Session ID missing");
        assertThat(rest.statusCode()).isEqualTo(401);
    }

    @Test
    void aPersonTheDirectoryPushedIsAdoptedOnFirstSignIn() throws Exception {

        // given
        var email = unique("bob") + "@acme.test";
        var scimId = UUID.randomUUID().toString();
        SelfHostedDatabase.asOwner("INSERT INTO identity_user (organization_id, display_name, email, created_at,"
                + " updated_at, version, active, scim_id, arrival) VALUES ('"
                + SelfHostedTestInfrastructure.ORGANIZATION_ID + "', 'Bob Example', '" + email + "', now(), now(), 0,"
                + " true, '" + scimId + "', 'SYNCED')");
        var subject = unique("bob-oid");

        // when
        var actual = get("/axonia/me", issuer.token(subject, Map.of("email", email, "name", "Bob Example")));

        // then
        assertThat(actual.statusCode()).isEqualTo(200);
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM identity_user WHERE email = '" + email + "'"))
                .isOne();
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM identity_user WHERE email = '" + email
                + "' AND user_id = '" + subject + "' AND scim_id = '" + scimId + "'")).isOne();
    }

    @Test
    void anAddressTheTokenCallsUnverifiedNeverAdoptsAPersonTheDirectoryPushed() throws Exception {

        // given
        var email = unique("carol") + "@acme.test";
        var scimId = pushedByTheDirectory(email);
        var subject = unique("mallory-oid");

        // when
        var actual = get("/axonia/me",
                issuer.token(subject, Map.of("email", email, "email_verified", false, "name", "Mallory")));

        // then
        assertNoAdoption(actual, email, scimId, subject);
    }

    @Test
    void anUnverifiedAddressEchoedInTheUsernameNeverAdoptsAPersonTheDirectoryPushed() throws Exception {

        // given
        var email = unique("carol") + "@acme.test";
        var scimId = pushedByTheDirectory(email);
        var subject = unique("mallory-oid");

        // when
        var actual = get("/axonia/me", issuer.token(subject, Map.of("email", email, "email_verified", false,
                "preferred_username", email.toUpperCase(Locale.ROOT), "name", "Mallory")));

        // then
        assertNoAdoption(actual, email, scimId, subject);
    }

    @Test
    void refusesATokenIssuedToAnApplication() throws Exception {

        // given
        var appOnly = issuer.token(unique("service-principal"), Map.of("idtyp", "app"));

        // when
        var actual = get("/axonia/me", appOnly);

        // then
        assertThat(actual.statusCode()).isEqualTo(401);
    }

    private static String pushedByTheDirectory(String email) throws SQLException {

        var scimId = UUID.randomUUID().toString();
        SelfHostedDatabase.asOwner("INSERT INTO identity_user (organization_id, display_name, email, created_at,"
                + " updated_at, version, active, scim_id, arrival) VALUES ('"
                + SelfHostedTestInfrastructure.ORGANIZATION_ID + "', 'Carol Example', '" + email + "', now(), now(), 0,"
                + " true, '" + scimId + "', 'SYNCED')");
        return scimId;
    }

    private static void assertNoAdoption(HttpResponse<String> actual, String email, String scimId, String subject)
            throws SQLException {

        assertThat(actual.statusCode()).isEqualTo(200);
        assertThat(actual.body()).doesNotContainIgnoringCase(email);
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM identity_user WHERE scim_id = '" + scimId
                + "' AND user_id IS NULL AND email = '" + email + "'")).isOne();
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM identity_user WHERE user_id = '" + subject
                + "' AND email IS NULL AND scim_id <> '" + scimId + "'")).isOne();
    }

    private String owner(String prefix) {

        return issuer.token(unique(prefix), Map.of("groups", List.of(SelfHostedTestInfrastructure.OWNER_GROUP)));
    }

    private HttpResponse<String> get(String path, String bearer) throws IOException, InterruptedException {

        return http.send(request(path, bearer).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String bearer, Map<String, Object> body)
            throws IOException, InterruptedException {

        return http.send(
                request(path, bearer).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String bearer) {

        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header(SelfHostedRequests.EDGE_HEADER, SelfHostedTestInfrastructure.EDGE_SECRET)
                .header("Authorization", "Bearer " + bearer);
    }

    private static long profilesOf(String subject, String organizationId) throws SQLException {

        return SelfHostedDatabase.countAsOwner("SELECT count(*) FROM identity_user WHERE user_id = '" + subject
                + "' AND organization_id = '" + organizationId + "'");
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
