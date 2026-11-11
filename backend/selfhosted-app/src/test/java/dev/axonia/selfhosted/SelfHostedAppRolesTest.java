package dev.axonia.selfhosted;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import dev.axonia.identity.application.scim.ScimTokens;
import dev.axonia.shared.api.tenancy.OrganizationScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An installation that reads Entra's app roles as its groups, fed by Entra's SCIM provisioning: the token's
 * {@code roles} say nothing about which directory groups a person is in, so a role bound to a synced group reaches its
 * members through the directory.
 */
class SelfHostedAppRolesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String START_COMMUNITIES = "community:create";

    private static ConfigurableApplicationContext application;

    private static String scimToken;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void foundAnInstallationReadingAppRoles() throws SQLException {

        var installation = FreshInstallation.create();
        application = SelfHostedTestInfrastructure.start(installation.settings(
                Map.of("axonia.selfhosted.oidc.subject-claim", "oid", "axonia.selfhosted.oidc.groups-claim", "roles")));
        var organizationId = SelfHostedTestInfrastructure.ORGANIZATION_ID;
        scimToken = OrganizationScope.runAs(organizationId,
                () -> application.getBean(ScimTokens.class).generate(organizationId, "entra", null));
    }

    @AfterAll
    static void stop() {

        application.close();
    }

    @Test
    void aRoleBoundToADirectoryGroupReachesAMemberWhoseTokenCarriesAnAppRole() throws Exception {

        // given
        var objectId = UUID.randomUUID().toString();
        var group = pushGroupOf(scimIdOf(pushUser(objectId)));
        bindToGroup(group, roleGranting(START_COMMUNITIES));

        // when
        var actual = permissionsOf(objectId, List.of("member"));

        // then
        assertThat(actual).contains(START_COMMUNITIES);
    }

    @Test
    void aRoleBoundToADirectoryGroupReachesAMemberWhoseTokenCarriesNoAppRole() throws Exception {

        // given
        var objectId = UUID.randomUUID().toString();
        var group = pushGroupOf(scimIdOf(pushUser(objectId)));
        bindToGroup(group, roleGranting(START_COMMUNITIES));

        // when
        var actual = permissionsOf(objectId, List.of());

        // then
        assertThat(actual).contains(START_COMMUNITIES);
    }

    @Test
    void aRoleBoundToADirectoryGroupStaysWithItsMembers() throws Exception {

        // given
        var group = pushGroupOf(scimIdOf(pushUser(UUID.randomUUID().toString())));
        bindToGroup(group, roleGranting(START_COMMUNITIES));
        var outsider = UUID.randomUUID().toString();
        pushUser(outsider);

        // when
        var actual = permissionsOf(outsider, List.of("member"));

        // then
        assertThat(actual).doesNotContain(START_COMMUNITIES);
    }

    private List<String> permissionsOf(String objectId, List<String> appRoles)
            throws IOException, InterruptedException {

        var token = MockOidcIssuer.shared().token(unique("pairwise"), Map.of("oid", objectId, "roles", appRoles));
        var response = http.send(withBearer("/axonia/access/me", token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).path("permissions").valueStream().map(JsonNode::asString).toList();
    }

    private long roleGranting(String permission) throws IOException, InterruptedException {

        var created = asOwner("/axonia/roles",
                Map.of("name", unique("Role"), "description", "", "permissions", List.of(permission)));
        assertThat(created.statusCode()).isEqualTo(201);
        return JSON.readTree(created.body()).path("id").asLong();
    }

    /** Binds the key the Group access page offers for the group, as an Owner picking it there does. */
    private void bindToGroup(String displayName, long roleId) throws IOException, InterruptedException {

        var listed = http.send(withBearer("/axonia/admin/idp-groups", owner()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var key = JSON.readTree(listed.body()).path("groups").valueStream()
                .filter(group -> displayName.equals(group.path("displayName").asString())).findFirst().orElseThrow()
                .path("groupId").asString();
        var bound = asOwner("/axonia/role-bindings",
                Map.of("subjectType", "GROUP", "subjectId", key, "roleId", roleId, "subjectLabel", displayName));
        assertThat(bound.statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> asOwner(String path, Map<String, Object> body)
            throws IOException, InterruptedException {

        return http.send(
                withBearer(path, owner()).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String owner() {

        return MockOidcIssuer.shared().token(unique("owner"), Map.of("oid", UUID.randomUUID().toString(), "roles",
                List.of(SelfHostedTestInfrastructure.OWNER_GROUP)));
    }

    private HttpResponse<String> pushUser(String objectId) throws IOException, InterruptedException {

        var body = Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"), "userName",
                unique("dana") + "@acme.test", "externalId", objectId, "displayName", "Directory Person", "active",
                true);
        var pushed = scim("/scim/v2/Users", body);
        assertThat(pushed.statusCode()).isEqualTo(201);
        return pushed;
    }

    private String pushGroupOf(String memberScimId) throws IOException, InterruptedException {

        var displayName = unique("Engineering");
        var body = Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:Group"), "displayName", displayName,
                "externalId", UUID.randomUUID().toString(), "members", List.of(Map.of("value", memberScimId)));
        assertThat(scim("/scim/v2/Groups", body).statusCode()).isEqualTo(201);
        return displayName;
    }

    private HttpResponse<String> scim(String path, Map<String, Object> body) throws IOException, InterruptedException {

        return http.send(
                request(path).header("Authorization", "Bearer " + scimToken)
                        .header("Content-Type", "application/scim+json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder withBearer(String path, String token) {

        return request(path).header("Authorization", "Bearer " + token);
    }

    private static HttpRequest.Builder request(String path) {

        var port = application.getEnvironment().getProperty("local.server.port");
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header(SelfHostedRequests.EDGE_HEADER, SelfHostedTestInfrastructure.EDGE_SECRET);
    }

    private static String scimIdOf(HttpResponse<String> pushed) {

        return JSON.readTree(pushed.body()).path("id").asString();
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
