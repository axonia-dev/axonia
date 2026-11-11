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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An installation's ownership is anchored in its configured owner group, bound again at every start, so the Owner role
 * granted directly to a person is as revocable as any other role: nothing removed inside the product can leave the
 * installation without an Owner.
 */
class SelfHostedOwnershipTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ConfigurableApplicationContext application;

    private static String scimToken;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void foundAnInstallation() throws SQLException {

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
    void theOnlyPersonGrantedOwnerDirectlyCanHaveItRevoked() throws Exception {

        // given - a provisioned person who never signed in, made Owner beside the configured owner group
        var objectId = UUID.randomUUID().toString();
        pushUser(objectId);
        var granted = asOwner(request("/axonia/role-bindings").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("subjectType", "USER",
                        "subjectId", objectId, "roleId", ownerRoleId(), "subjectLabel", "Directory Person")))));
        assertThat(granted.statusCode()).isEqualTo(201);
        var bindingId = JSON.readTree(granted.body()).path("id").asLong();

        // when
        var revoked = asOwner(request("/axonia/role-bindings/" + bindingId).DELETE());

        // then
        assertThat(revoked.statusCode()).isEqualTo(204);
    }

    private long ownerRoleId() throws IOException, InterruptedException {

        var roles = JSON.readTree(asOwner(request("/axonia/roles").GET()).body());
        return roles.valueStream().filter(role -> "Owner".equals(role.path("name").asString())).findFirst()
                .orElseThrow().path("id").asLong();
    }

    private HttpResponse<String> asOwner(HttpRequest.Builder request) throws IOException, InterruptedException {

        var owner = MockOidcIssuer.shared().token(unique("owner"), Map.of("oid", UUID.randomUUID().toString(), "roles",
                List.of(SelfHostedTestInfrastructure.OWNER_GROUP)));
        return http.send(request.header("Authorization", "Bearer " + owner).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void pushUser(String objectId) throws IOException, InterruptedException {

        var body = Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"), "userName",
                unique("dana") + "@acme.test", "externalId", objectId, "displayName", "Directory Person", "active",
                true);
        var pushed = http.send(
                request("/scim/v2/Users").header("Authorization", "Bearer " + scimToken)
                        .header("Content-Type", "application/scim+json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(pushed.statusCode()).isEqualTo(201);
    }

    private static HttpRequest.Builder request(String path) {

        var port = application.getEnvironment().getProperty("local.server.port");
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header(SelfHostedRequests.EDGE_HEADER, SelfHostedTestInfrastructure.EDGE_SECRET);
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
