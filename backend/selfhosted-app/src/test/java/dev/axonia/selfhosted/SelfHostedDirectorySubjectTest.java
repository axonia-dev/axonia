package dev.axonia.selfhosted;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import dev.axonia.identity.application.scim.ScimTokens;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An installation founded on Entra's {@code oid}, fed by Entra's SCIM provisioning: a person the directory pushes holds
 * their subject before they ever sign in, so they can be credited from the start, and their first sign-in lands on that
 * same row by subject rather than by address.
 */
class SelfHostedDirectorySubjectTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    private static ConfigurableApplicationContext application;

    private static FreshInstallation installation;

    private static String scimToken;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void foundAnInstallationOnTheObjectId() throws SQLException {

        installation = FreshInstallation.create();
        application = SelfHostedTestInfrastructure
                .start(installation.settings(Map.of("axonia.selfhosted.oidc.subject-claim", "oid")));
        var organizationId = SelfHostedTestInfrastructure.ORGANIZATION_ID;
        scimToken = OrganizationScope.runAs(organizationId,
                () -> application.getBean(ScimTokens.class).generate(organizationId, "entra", null));
    }

    @AfterAll
    static void stop() {

        application.close();
    }

    @Test
    void anObjectIdTheDirectoryPushesIsTheSubjectBeforeAnySignIn() throws Exception {

        // given
        var objectId = UUID.randomUUID().toString();

        // when
        var pushed = push(unique("dana") + "@acme.test", objectId);

        // then
        assertThat(pushed.statusCode()).isEqualTo(201);
        assertThat(rowOf(scimIdOf(pushed))).isEqualTo(objectId + "|" + objectId + "|f");
    }

    @Test
    void aMailNicknameIsNeverTakenForTheSubject() throws Exception {

        // when
        var pushed = push(unique("erin") + "@acme.test", "M0793512");

        // then
        assertThat(pushed.statusCode()).isEqualTo(201);
        assertThat(rowOf(scimIdOf(pushed))).isEqualTo("null|M0793512|f");
    }

    @Test
    void remappingTheExternalIdToTheObjectIdGivesAPendingPersonTheirSubject() throws Exception {

        // given
        var scimId = scimIdOf(push(unique("frank") + "@acme.test", "M0793513"));
        var objectId = UUID.randomUUID().toString();

        // when
        var patched = patch(scimId, "Replace", objectId.toUpperCase(Locale.ROOT));

        // then
        assertThat(patched.statusCode()).isEqualTo(200);
        assertThat(rowOf(scimId)).isEqualTo(objectId + "|" + objectId.toUpperCase(Locale.ROOT) + "|f");
    }

    @Test
    void anExternalIdAddedByPatchIsAppliedAsAReplace() throws Exception {

        // given
        var scimId = scimIdOf(push(unique("gina") + "@acme.test", "M0793514"));
        var objectId = UUID.randomUUID().toString();

        // when
        var patched = patch(scimId, "Add", objectId);

        // then
        assertThat(patched.statusCode()).isEqualTo(200);
        assertThat(rowOf(scimId)).isEqualTo(objectId + "|" + objectId + "|f");
    }

    @Test
    void anExternalIdAnotherPersonHoldsIsAConflict() throws Exception {

        // given
        var objectId = UUID.randomUUID().toString();
        push(unique("hank") + "@acme.test", objectId);
        var scimId = scimIdOf(push(unique("ivy") + "@acme.test", "M0793515"));

        // when
        var patched = patch(scimId, "Replace", objectId);

        // then
        assertThat(patched.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(patched.body()).path("scimType").asString()).isEqualTo("uniqueness");
        assertThat(rowOf(scimId)).isEqualTo("null|M0793515|f");
    }

    @Test
    void aSubjectSomebodyAlreadySignedInAsStaysWithThem() throws Exception {

        // given
        var objectId = UUID.randomUUID().toString();
        assertThat(me(objectId, unique("jack") + "@elsewhere.test").statusCode()).isEqualTo(200);
        var scimId = scimIdOf(push(unique("jack") + "@acme.test", "M0793516"));

        // when
        var patched = patch(scimId, "Replace", objectId);

        // then
        assertThat(patched.statusCode()).isEqualTo(200);
        assertThat(rowOf(scimId)).isEqualTo("null|" + objectId + "|f");
        assertThat(installation.rows("SELECT signed_in FROM identity_user WHERE user_id = '" + objectId + "'"))
                .containsExactly("t");
    }

    @Test
    void theFirstSignInLandsOnThePushedRowByObjectIdWhateverTheAddress() throws Exception {

        // given
        var objectId = UUID.randomUUID().toString();
        var scimId = scimIdOf(push(unique("kate") + "@acme.test", objectId));

        // when
        var actual = me(objectId, unique("kate") + "@renamed.test");

        // then
        assertThat(actual.statusCode()).isEqualTo(200);
        assertThat(installation.rows("SELECT scim_id, signed_in FROM identity_user WHERE user_id = '" + objectId + "'"))
                .containsExactly(scimId + "|t");
    }

    private HttpResponse<String> push(String email, String externalId) throws IOException, InterruptedException {

        var body = Map.of("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"}, "userName", email,
                "externalId", externalId, "displayName", "Directory Person", "active", true);
        return http.send(
                scim("/scim/v2/Users").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String scimId, String op, String externalId)
            throws IOException, InterruptedException {

        var body = Map.of("schemas", new String[]{PATCH_OP}, "Operations",
                new Object[]{Map.of("op", op, "path", "externalId", "value", externalId)});
        return http.send(
                scim("/scim/v2/Users/" + scimId)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> me(String objectId, String email) throws IOException, InterruptedException {

        var token = MockOidcIssuer.shared().token(unique("pairwise"),
                Map.of("oid", objectId, "email", email, "name", "Directory Person"));
        return http.send(request("/axonia/me").header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder scim(String path) {

        return request(path).header("Authorization", "Bearer " + scimToken).header("Content-Type",
                "application/scim+json");
    }

    private static HttpRequest.Builder request(String path) {

        var port = application.getEnvironment().getProperty("local.server.port");
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header(SelfHostedRequests.EDGE_HEADER, SelfHostedTestInfrastructure.EDGE_SECRET);
    }

    private static String scimIdOf(HttpResponse<String> pushed) {

        return JSON.readTree(pushed.body()).path("id").asString();
    }

    private static String rowOf(String scimId) throws SQLException {

        var rows = installation
                .rows("SELECT user_id, external_id, signed_in FROM identity_user WHERE scim_id = '" + scimId + "'");
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
