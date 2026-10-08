package dev.axonia.identity.scim;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.identity.IdentityClaims;
import dev.axonia.identity.UserService;
import dev.axonia.identity.internal.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces, end to end over HTTP against a real database, the two defects that make a signed-in user unprovisionable
 * through Entra's SCIM pre-flight: their {@code scim_id} is null, so Entra's match-by-userName finds a resource with no
 * linkable {@code id}; and the create Entra falls back to when matching fails comes back {@code 409} with no
 * {@code scimType}, so Entra cannot recognise it as a recoverable uniqueness conflict.
 *
 * <p>
 * This is the regression gate for the fixes that follow: it is written to fail today, and must keep failing until both
 * defects are fixed.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("DEV")
class ScimExistingUserProvisioningTest {

    private static final String ORG = "existing-user-org";
    private static final String USER_SUB = "sub-bootstrap-admin";
    private static final String EMAIL = "admin@acme.test";
    private static final String DISPLAY_NAME = "Bootstrap Admin";

    @LocalServerPort
    private int port;

    @Autowired
    private ScimTokens scimTokens;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aUserWhoSignedInFirstIsMatchableAndItsConflictIsRecoverable() throws Exception {

        ensureOrg(ORG, "Existing User Org");
        String token = scimTokens.generate(ORG, "existing-user-test", null);

        // given - the person exists because they signed in, not because SCIM created them
        userService.provision(IdentityClaims.builder().organizationId(ORG).userId(USER_SUB).displayName(DISPLAY_NAME)
                .email(EMAIL).build());

        // when - Entra's pre-flight match
        var found = getJson(token,
                "/scim/v2/Users?filter=" + URLEncoder.encode("userName eq \"" + EMAIL + "\"", StandardCharsets.UTF_8));

        // then - the resource must be linkable; RFC 7643 3.1 requires a non-empty id
        var resource = found.get("Resources").get(0);
        assertThat(resource.get("id").isNull()).isFalse();
        assertThat(resource.get("id").asString()).isNotBlank();

        // when - the create Entra falls back to when matching fails
        var conflict = postJson(token, "/scim/v2/Users", """
                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                 "userName":"%s","displayName":"%s","active":true}
                """.formatted(EMAIL, DISPLAY_NAME));

        // then - RFC 7644 3.3: 409 carrying scimType uniqueness, which is what lets Entra reconcile
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(jsonMapper.readTree(conflict.body()).get("scimType").asString()).isEqualTo("uniqueness");
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByOrganizationIdAndUserId(ORG, USER_SUB).ifPresent(userRepository::delete);
        jdbc.update("DELETE FROM scim_token WHERE organization_id = ?", ORG);
        jdbc.update("DELETE FROM organization WHERE id = ?", ORG);
    }

    private void ensureOrg(String organizationId, String name) {
        var exists = jdbc.queryForList("SELECT id FROM organization WHERE id = ?", organizationId);
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO organization (id, name, kind, created_at, updated_at)"
                    + " VALUES (?, ?, 'ENTERPRISE', now(), now())", organizationId, name);
        }
    }

    /**
     * The status is asserted before the body is parsed so that a failure reads as the status it was, rather than as an
     * NPE thrown several lines later by navigating an error document that has no {@code Resources}.
     */
    private JsonNode getJson(String bearer, String path) throws Exception {
        var response = scimRequest("GET", path, bearer, null);
        assertThat(response.statusCode()).as("GET %s returned %s", path, response.body()).isEqualTo(200);
        return jsonMapper.readTree(response.body());
    }

    private HttpResponse<String> postJson(String bearer, String path, String body) throws Exception {
        return scimRequest("POST", path, bearer, body);
    }

    private HttpResponse<String> scimRequest(String method, String path, String bearer, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + bearer).header("Content-Type", "application/scim+json");
        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(pub(body));
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.BodyPublisher pub(String json) {
        return json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.strip());
    }
}
