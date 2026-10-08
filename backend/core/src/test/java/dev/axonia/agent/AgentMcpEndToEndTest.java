package dev.axonia.agent;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import dev.axonia.CoreSpringBootTest;
import dev.axonia.expertise.ExpertisePreferenceService;
import dev.axonia.expertise.ExpertiseService;
import dev.axonia.expertise.Practitioner;
import dev.axonia.expertise.Tier;
import dev.axonia.identity.pat.MintPersonalAccessToken;
import dev.axonia.identity.pat.PersonalAccessTokens;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end behavioral proof of the agent MCP surface (Plan 2). Boots the full application against the
 * Testcontainers-managed Postgres (via the {@code jdbc:tc:} URL in {@code application-DEV.properties}), mints real
 * {@code axp_} personal access tokens through the {@link PersonalAccessTokens} port, then drives the {@code /mcp}
 * streamable endpoint with the MCP Java SDK client to verify the three-layer trust model. The surface is
 * Enterprise-only ({@link dev.axonia.agent.AgentAccess}), so every fixture organization here is ENTERPRISE.
 *
 * <p>
 * Assertions (each maps to a spec guarantee):
 * <ol>
 * <li>A scoped token lists exactly the 24 tools (surface allowlist + auth passes).</li>
 * <li>A scoped {@code topic.propose} call succeeds, a topic is created, and an {@code OK} audit row is written.</li>
 * <li>An unscoped {@code library.contribute} call is refused (scope layer), a {@code SCOPE_DENIED} audit row is
 * written, and the org's artifact count is unchanged.</li>
 * <li>A bogus bearer on {@code /mcp} is rejected with 401 (chain gate).</li>
 * <li>The same {@code axp_} token presented to the REST chain ({@code /axonia/**}) is rejected with 401 (surface
 * isolation).</li>
 * <li>{@code tools/list} contains exactly the 24 known tool names, and exactly two of them declare
 * {@code destructiveHint}: {@code session.detachArtifact} and {@code learning.remove}.</li>
 * <li>The scoped {@code topic.propose} guarantee holds identically in a second, independently seeded ENTERPRISE
 * organization: the tool code path does not depend on which organization it runs in.</li>
 * <li>The full library round trip: {@code library.contribute} then {@code session.attachArtifact} then
 * {@code session.artifacts} reads it back, re-attaching is idempotent, then {@code session.detachArtifact} removes it.
 * The session is seeded HELD, so a plain member's attach is legitimate: this exercises the open arm of the
 * schedule-management-unless-held rule. A companion test proves the other arm - a plain member is refused before the
 * session is held.</li>
 * <li>{@code session:attach} does not confer {@code session:detach}: the two scopes do not collapse.</li>
 * <li><strong>The claim this whole feature makes.</strong> An organizer backfills a past session through the agent
 * surface: a section, a slot recording another member as the presenter tagged {@code kafka}, and a recording of her
 * talk contributed in her name. She then appears in the expertise graph as a PRACTITIONER in {@code kafka}, read
 * through the graph's real read path, and the organizer appears in it not at all. Along the way the roster closes its
 * loop: {@code community.members} names her without her email, and the user id it reports, fed straight back into
 * {@code slot.addPresenter}, resolves exactly.</li>
 * <li>{@code community.members} lists the roster with display names and no emails, and {@code read} does not confer
 * {@code members:read}: a token without it is refused and a {@code SCOPE_DENIED} audit row is written.</li>
 * <li>The roster's domain floor: a {@code members:read} token whose owner is not a member of the community is refused,
 * and no name escapes the refusal. The scope is what its owner allowed; membership is what its owner may see.</li>
 * <li>The roster's pages neither repeat nor skip a member: a fixture seeded out of order comes back in the stable order
 * the surface pins, proving the ORDER BY is really rendered and not silently dropped.</li>
 * <li>A plain member crediting somebody else is told to omit the field, not that it is not a member: the domain's
 * organizer refusal survives the surface's error sanitizer with its message intact.</li>
 * <li>A contributor or proposer reference matching nobody, or several members, refuses the write and reaches the agent
 * saying which name failed and why, rather than as a bare "the action could not be completed".</li>
 * <li>The learning round trip: a {@code learning:declare} token adds an area through {@code learning.add},
 * {@code learning.demand} lists it as held, and {@code learning.remove} takes it back out of the demand.</li>
 * </ol>
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AgentMcpEndToEndTest {

    /**
     * The complete tool catalog. Assertion 6 checks {@code tools/list} is EXACTLY this set, and that exactly two tools
     * declare {@code destructiveHint} - {@code session.detachArtifact}, gated behind its own {@code session:detach}
     * scope, and {@code learning.remove}, which drops the date an area was named and takes back only the caller's own
     * statement. Every other tool is additive.
     */
    private static final Set<String> EXPECTED_TOOLS = Set.of("communities.list", "schedule.read", "session.get",
            "library.artifactTypes", "library.contribute", "topic.propose", "session.signup", "attendance.signal",
            "community.join", "library.search", "session.artifacts", "session.attachArtifact", "session.detachArtifact",
            "events.sectionTypes", "session.create", "session.addSection", "session.addSlot", "slot.addPresenter",
            "community.members", "actionItem.raise", "learning.demand", "learning.learners", "learning.add",
            "learning.remove");

    /** The destructive tools on the agent surface. Assertion 6 pins this list to prevent silent growth. */
    private static final Set<String> DESTRUCTIVE_TOOLS = Set.of("session.detachArtifact", "learning.remove");

    /** Reads a tool result's JSON text back into fields, e.g. the {@code sessionId} a create call reports. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Enterprise fixture. The organization row is seeded below, not assumed: the baseline seeds no data at all, so
    // 'default' exists only when some earlier class in the run happened to commit it and cleanup.sql spared it.
    private static final String ENTERPRISE_ORG = "default";
    private static final String ENTERPRISE_USER = "agent-ent-user";
    private static final String ENTERPRISE_SLUG = "agent-ent-guild";

    // A second member of ENTERPRISE_SLUG, distinct from the token's owner, so the backfill loop (assertion 10) can
    // prove a slot gets attributed to the person actually named as presenter rather than to whoever ran the tool.
    private static final String PRESENTER_USER = "agent-ent-presenter";
    private static final String PRESENTER_DISPLAY_NAME = "Backfill Presenter";

    // A REAL email on a member the roster returns. Without one anywhere in the fixture, assertion 11's "no @ in the
    // roster" could not fail whatever the mapping did, and would guard nothing.
    private static final String PRESENTER_EMAIL = "backfill.presenter@example.test";

    // A dedicated organizer, distinct from ENTERPRISE_USER, used only by the backfill loop (assertion 10), which
    // needs organizer rights to create and shape a past session. ENTERPRISE_USER stays a plain member for every
    // other assertion, which is what proves the trust model's domain floor: a token cannot exceed its owner.
    private static final String ORGANIZER_USER = "agent-ent-organizer";

    // An org user who is a member of no community. Every other seeded enterprise user is a member of ENTERPRISE_SLUG,
    // so without this one nothing would prove the roster's domain floor against the real endpoint (assertion 12).
    private static final String NON_MEMBER_USER = "agent-ent-outsider";

    // A community of its own for the paging proof (assertion 13), so the roster of ENTERPRISE_SLUG stays the small,
    // stable fixture the other assertions read. Seeded deliberately OUT of ascending user-id order: an ORDER BY that
    // Spring silently dropped would hand back insertion order, and that is exactly what must not pass.
    private static final String ROSTER_SLUG = "agent-ent-roster";
    private static final List<String> ROSTER_SEED_ORDER = List.of("roster-user-3", "roster-user-1", "roster-user-2");
    private static final List<String> ROSTER_ASCENDING = List.of(ENTERPRISE_USER, "roster-user-1", "roster-user-2",
            "roster-user-3");

    // A second, independently seeded ENTERPRISE org (assertion 7): it proves the tool code path works from a
    // second organization, not anything about kind.
    private static final String SECOND_ORG = "agent-second-e2e";
    private static final String SECOND_USER = "agent-second-user";
    private static final String SECOND_SLUG = "agent-second-guild";

    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    @LocalServerPort
    private int port;

    @Autowired
    private PersonalAccessTokens tokens;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The expertise graph's REAL read path, and the only one this test reads it through. Selecting from
     * {@code expertise_signal} would pass just as happily if the read model never surfaced the person at all, and "she
     * is in the table but invisible in the product" is a failure, not a success.
     */
    @Autowired
    private ExpertiseService expertise;

    /** The same graph seen from one person's side: what it holds about them, everywhere, whatever the community. */
    @Autowired
    private ExpertisePreferenceService expertiseProfile;

    /**
     * The OAuth resource-server chain needs a {@link JwtDecoder} bean to exist. Stubbed to reject any presented token
     * as a malformed JWT, exactly how the real Nimbus decoder behaves for a non-JWT {@code axp_} bearer. The
     * {@code axp_} tokens minted here never reach this decoder; they are consumed by the MCP chain's
     * AgentAuthenticationFilter.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        org.mockito.Mockito.when(jwtDecoder.decode(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new BadJwtException("not a JWT"));

        if (SEEDED.compareAndSet(false, true)) {
            // The enterprise org and its reference catalogs are all seeded here. Nothing in this fixture may be
            // assumed to exist: OrganizationLifecycleFilter rejects a token naming an unknown Organization with 401,
            // so an absent organization row fails every MCP call in this class before it reaches a tool.
            seedEnterpriseOrganization(ENTERPRISE_ORG);
            var enterpriseKindId = insertCommunityKind(ENTERPRISE_ORG);
            insertSectionType(ENTERPRISE_ORG);
            seedActiveMember(ENTERPRISE_ORG, ENTERPRISE_USER);
            seedCommunityWithKind(ENTERPRISE_ORG, ENTERPRISE_SLUG, ENTERPRISE_USER, enterpriseKindId);
            // A second member of the same community, distinct from ENTERPRISE_USER, so the MemberDirectory can
            // resolve PRESENTER_DISPLAY_NAME to somebody other than the token's owner. Carries a real email, which
            // is what gives assertion 11 something to leak.
            seedActiveMember(ENTERPRISE_ORG, PRESENTER_USER, PRESENTER_DISPLAY_NAME, PRESENTER_EMAIL);
            seedMembership(ENTERPRISE_ORG, ENTERPRISE_SLUG, PRESENTER_USER);
            // The backfill loop (assertion 10) creates and shapes a past session, which requires organizer rights;
            // a plain MEMBER cannot. A dedicated ORGANIZER_USER is seeded and promoted here, kept separate from
            // ENTERPRISE_USER, so ENTERPRISE_USER stays a plain member for every other assertion in this class.
            seedActiveMember(ENTERPRISE_ORG, ORGANIZER_USER);
            seedMembership(ENTERPRISE_ORG, ENTERPRISE_SLUG, ORGANIZER_USER);
            promoteToOrganizer(ENTERPRISE_ORG, ENTERPRISE_SLUG, ORGANIZER_USER);

            // An org user with no membership anywhere, and a community of four members seeded out of order: the two
            // fixtures the roster's own guarantees (a domain floor, and a stable page boundary) are proven against.
            seedActiveMember(ENTERPRISE_ORG, NON_MEMBER_USER);
            seedCommunityWithKind(ENTERPRISE_ORG, ROSTER_SLUG, ENTERPRISE_USER, enterpriseKindId);
            for (var rosterUser : ROSTER_SEED_ORDER) {
                seedActiveMember(ENTERPRISE_ORG, rosterUser);
                seedMembership(ENTERPRISE_ORG, ROSTER_SLUG, rosterUser);
            }

            // A second, independently seeded ENTERPRISE organization. The MCP surface is Enterprise-only (the
            // agent-access-and-open-docs kind gate), so a HUB org can no longer authenticate here at all; this proves
            // the tool code path works from a second organization rather than proving anything about kind.
            seedEnterpriseOrganization(SECOND_ORG);
            seedActiveMember(SECOND_ORG, SECOND_USER);
            var secondKindId = insertCommunityKind(SECOND_ORG);
            seedCommunityWithKind(SECOND_ORG, SECOND_SLUG, SECOND_USER, secondKindId);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 1: a scoped token lists exactly the 24 tools.
    // -------------------------------------------------------------------------

    @Test
    void scopedToken_listsExactlyTheTwentyFourTools() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read", "topic:propose"));
        var client = connect(bearer);
        try {
            client.initialize();
            var names = toolNames(client);
            assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 2: scoped topic.propose succeeds; a topic exists; an OK audit row is written.
    // -------------------------------------------------------------------------

    @Test
    void scopedTopicPropose_succeeds_createsTopic_andWritesOkAuditRow() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read", "topic:propose"));
        var client = connect(bearer);
        try {
            client.initialize();
            var result = proposeTopic(client, ENTERPRISE_SLUG);

            assertThat(result.isError()).as("scoped topic.propose must succeed: %s", textOf(result))
                    .isNotEqualTo(Boolean.TRUE);
            assertThat(topicCount(ENTERPRISE_ORG)).as("a topic must have been created").isGreaterThanOrEqualTo(1);
            assertThat(auditOutcomes(ENTERPRISE_ORG, ENTERPRISE_USER, "topic.propose"))
                    .as("an OK audit row for topic.propose must exist").contains("OK");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 3: unscoped library.contribute is refused; SCOPE_DENIED audit row; no artifact created.
    // -------------------------------------------------------------------------

    @Test
    void unscopedLibraryContribute_isRefused_writesScopeDenied_andCreatesNoArtifact() {
        // Same {read, topic:propose} token, deliberately WITHOUT library:contribute.
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read", "topic:propose"));
        var client = connect(bearer);
        // A delta, not an absolute zero: assertion 8 legitimately contributes an artifact to this same org, and JUnit
        // gives no ordering guarantee between the two.
        var before = artifactCount(ENTERPRISE_ORG);
        try {
            client.initialize();
            // Valid args in the confirmed NESTED shape, supplying only the REQUIRED fields (description/tags are
            // now optional). The call therefore passes input validation and reaches the scope gate, which denies it.
            var params = new LinkedHashMap<String, Object>();
            params.put("slug", ENTERPRISE_SLUG);
            params.put("title", "Should never be stored");
            params.put("typeId", 1L);
            params.put("url", "https://example.test/denied");
            var result = client.callTool(new McpSchema.CallToolRequest("library.contribute", Map.of("params", params)));

            assertThat(result.isError()).as("unscoped library.contribute must be an error result")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(result)).as("the refusal must name the missing scope").contains("library:contribute");
            assertThat(auditOutcomes(ENTERPRISE_ORG, ENTERPRISE_USER, "library.contribute"))
                    .as("a SCOPE_DENIED audit row for library.contribute must exist").contains("SCOPE_DENIED");
            assertThat(artifactCount(ENTERPRISE_ORG)).as("no artifact may be created by a refused call")
                    .isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM artifact WHERE organization_id = ? AND title = ?",
                    Long.class, ENTERPRISE_ORG, "Should never be stored"))
                    .as("the refused artifact must not exist by title").isZero();
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 4: a bogus bearer on /mcp is rejected with 401.
    // -------------------------------------------------------------------------

    @Test
    void bogusBearerOnMcp_isRejectedWith401() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                .header("Authorization", "Bearer axp_not-a-real-token").header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream").POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("an unknown bearer must be rejected by the MCP chain").isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json;charset=UTF-8");
    }

    @Test
    void toolsListWithoutASession_answers400WithTheJsonRpcErrorAndNoThrowableInternals() throws Exception {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read"));
        var request = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp")).header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        var error = JSON.readTree(response.body());
        assertThat(error.path("code").asInt()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
        assertThat(error.path("message").asString()).isEqualTo("Session ID missing");
        assertThat(response.body()).doesNotContain("stackTrace", "cause", "suppressed", "localizedMessage");
    }

    // -------------------------------------------------------------------------
    // Assertion 5: the same axp_ token on the REST chain is rejected with 401 (surface isolation).
    // -------------------------------------------------------------------------

    @Test
    void axpToken_isRejectedByRestChainWith401() throws Exception {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read"));
        var request = HttpRequest.newBuilder(URI.create(baseUrl() + "/axonia/access/me"))
                .header("Authorization", "Bearer " + bearer).GET().build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("an axp_ personal token must not authenticate the JWT-gated REST chain")
                .isEqualTo(401);
    }

    // -------------------------------------------------------------------------
    // Assertion 6: tools/list is EXACTLY the 24 known names, and exactly two declare destructiveHint.
    // -------------------------------------------------------------------------

    @Test
    void toolCatalog_isExactlyTheTwentyFourKnownTools_andOnlyDetachAndLearningRemoveAreDestructive() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read"));
        var client = connect(bearer);
        try {
            client.initialize();
            var tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);

            // Read the declared annotation rather than guessing from the name: "detachArtifact" contains none of
            // "delete"/"archive"/"remove", so a substring heuristic would pass while asserting nothing.
            var destructive = tools.stream().filter(t -> t.annotations() != null)
                    .filter(t -> Boolean.TRUE.equals(t.annotations().destructiveHint())).map(McpSchema.Tool::name)
                    .collect(Collectors.toSet());
            assertThat(destructive).as("exactly two destructive tools: detaching an artifact and removing an area")
                    .isEqualTo(DESTRUCTIVE_TOOLS);
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 7: the topic.propose guarantee holds identically in a second, independently seeded organization.
    // -------------------------------------------------------------------------

    @Test
    void secondOrganization_topicPropose_succeeds() {
        var bearer = mint(SECOND_ORG, SECOND_USER, Set.of("read", "topic:propose"));
        var client = connect(bearer);
        try {
            client.initialize();
            var result = proposeTopic(client, SECOND_SLUG);

            assertThat(result.isError()).as("topic.propose must succeed in a second org too: %s", textOf(result))
                    .isNotEqualTo(Boolean.TRUE);
            assertThat(topicCount(SECOND_ORG)).as("a topic must have been created in the second org")
                    .isGreaterThanOrEqualTo(1);
            assertThat(auditOutcomes(SECOND_ORG, SECOND_USER, "topic.propose"))
                    .as("an OK audit row for the second org's topic.propose must exist").contains("OK");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // MCP client wiring
    // -------------------------------------------------------------------------
    // Assertion 8: contribute -> attach -> read back -> re-attach (idempotent) -> detach.
    // -------------------------------------------------------------------------

    @Test
    void contributeAttachReadBackAndDetach() {
        // HELD: the open arm of the schedule-management-unless-held rule, so a plain member's attach is legitimate.
        var sessionId = seedSession(ENTERPRISE_ORG, ENTERPRISE_SLUG, "HELD");
        var typeId = seedArtifactType(ENTERPRISE_ORG);
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER,
                Set.of("read", "library:contribute", "session:attach", "session:detach"));
        var client = connect(bearer);
        try {
            client.initialize();

            var contributeParams = new LinkedHashMap<String, Object>();
            contributeParams.put("slug", ENTERPRISE_SLUG);
            contributeParams.put("title", "Session recording");
            contributeParams.put("typeId", typeId);
            contributeParams.put("url", "https://example.com/rec.mp4");
            var contributed = client
                    .callTool(new McpSchema.CallToolRequest("library.contribute", Map.of("params", contributeParams)));
            assertThat(contributed.isError()).as("library.contribute must succeed: %s", textOf(contributed))
                    .isNotEqualTo(Boolean.TRUE);
            var artifactId = artifactIdByTitle(ENTERPRISE_ORG, "Session recording");

            // segmentId is deliberately omitted: a session-level attachment. V7 made segment_id nullable and rebuilt
            // uq_attachment with NULLS NOT DISTINCT precisely so this stays idempotent.
            var attached = client.callTool(new McpSchema.CallToolRequest("session.attachArtifact",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId, "artifactId", artifactId)));
            assertThat(attached.isError()).as("attach must succeed with session:attach: %s", textOf(attached))
                    .isNotEqualTo(Boolean.TRUE);

            var listed = client.callTool(new McpSchema.CallToolRequest("session.artifacts",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId)));
            assertThat(textOf(listed)).as("the attached recording must be readable back").contains("Session recording");

            var again = client.callTool(new McpSchema.CallToolRequest("session.attachArtifact",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId, "artifactId", artifactId)));
            assertThat(again.isError()).as("re-attaching must not conflict: %s", textOf(again))
                    .isNotEqualTo(Boolean.TRUE);
            assertThat(attachmentCount(ENTERPRISE_ORG, artifactId)).as("re-attaching must not duplicate").isEqualTo(1L);

            var detached = client.callTool(new McpSchema.CallToolRequest("session.detachArtifact",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId, "artifactId", artifactId)));
            assertThat(detached.isError()).as("detach must succeed with session:detach: %s", textOf(detached))
                    .isNotEqualTo(Boolean.TRUE);

            var after = client.callTool(new McpSchema.CallToolRequest("session.artifacts",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId)));
            assertThat(after.isError()).as("session.artifacts must succeed after detach: %s", textOf(after))
                    .isNotEqualTo(Boolean.TRUE);
            assertThat(textOf(after)).as("the artifact must be gone after detach").doesNotContain("Session recording");
            assertThat(attachmentCount(ENTERPRISE_ORG, artifactId)).as("detach must remove the attachment row")
                    .isZero();
        } finally {
            close(client);
        }
    }

    /**
     * The other arm of assertion 8's rule, at the same MCP layer: a plain member holding {@code session:attach} still
     * cannot attach to a session that has not been held yet, because the schedule-management gate sits in front of the
     * scope gate's grant. {@code dev.axonia.knowledge.internal.ResourceAttachAuthorizerTest} proves the rule itself;
     * this proves it is actually wired to {@code session.attachArtifact} rather than bypassed on this surface.
     */
    @Test
    void plainMemberAttachingBeforeHeld_isRefused() {
        var sessionId = seedSession(ENTERPRISE_ORG, ENTERPRISE_SLUG, "SCHEDULED");
        var typeId = seedArtifactType(ENTERPRISE_ORG, "E2E Attach Before Held");
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read", "library:contribute", "session:attach"));
        var client = connect(bearer);
        try {
            client.initialize();

            var contributeParams = new LinkedHashMap<String, Object>();
            contributeParams.put("slug", ENTERPRISE_SLUG);
            contributeParams.put("title", "Not yet held");
            contributeParams.put("typeId", typeId);
            contributeParams.put("url", "https://example.test/not-yet-held");
            var contributed = client
                    .callTool(new McpSchema.CallToolRequest("library.contribute", Map.of("params", contributeParams)));
            assertThat(contributed.isError()).as("contribute must succeed: %s", textOf(contributed))
                    .isNotEqualTo(Boolean.TRUE);
            var artifactId = artifactIdByTitle(ENTERPRISE_ORG, "Not yet held");

            var attached = client.callTool(new McpSchema.CallToolRequest("session.attachArtifact",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId, "artifactId", artifactId)));

            assertThat(attached.isError())
                    .as("a plain member holding session:attach must still be refused before the session is held")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(attached)).as("the domain refusal, sanitized, not a scope refusal")
                    .contains("not a member of this community");
            assertThat(attachmentCount(ENTERPRISE_ORG, artifactId)).as("a refused attach must create no attachment row")
                    .isZero();
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 9: session:attach does NOT confer session:detach.
    // -------------------------------------------------------------------------

    @Test
    void attachScopeDoesNotConferDetach() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read", "session:attach"));
        var client = connect(bearer);
        try {
            client.initialize();
            var result = client.callTool(new McpSchema.CallToolRequest("session.detachArtifact",
                    Map.of("slug", ENTERPRISE_SLUG, "sessionId", 1L, "artifactId", 1L)));

            assertThat(result.isError()).as("detach without session:detach must be an error result")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(result)).as("the refusal must name the missing scope").contains("session:detach");
            assertThat(auditOutcomes(ENTERPRISE_ORG, ENTERPRISE_USER, "session.detachArtifact"))
                    .as("a SCOPE_DENIED audit row for session.detachArtifact must exist").contains("SCOPE_DENIED");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 10: the claim. An organizer backfills a talk somebody else gave, and the expertise graph credits
    // the speaker and not the organizer. Before this feature, an organizer migrating two years of a guild's history
    // credited THEMSELVES across the whole guild's subject matter, and nobody would ever have noticed.
    // -------------------------------------------------------------------------

    @Test
    void backfillLoop_creditsTheSpeakerInTheExpertiseGraph_andTheMigratingOrganizerNotAtAll() {
        var bearer = mint(ENTERPRISE_ORG, ORGANIZER_USER,
                Set.of("read", "schedule:manage", "library:contribute", "session:attach", "members:read"));
        var client = connect(bearer);
        try {
            client.initialize();

            var sectionTypeId = firstSectionTypeId(client);

            var sessionId = callForLong(client, "session.create", Map.of("params", Map.of("slug", ENTERPRISE_SLUG,
                    "title", "Guild #42", "scheduledAt", "2025-03-14T17:00:00Z", "durationMinutes", 60)), "sessionId");

            var sectionId = callForLong(client, "session.addSection",
                    Map.of("sessionId", sessionId, "sectionTypeId", sectionTypeId, "title", "Talks"), "sectionId");

            // ORGANIZER_USER holds the token; PRESENTER_USER is a different member of the same community. The slot
            // must end up attributed to the presenter, never to the token's owner. The tags are what give a talk that
            // was never proposed a subject, and so what makes a backfilled history visible to the expertise graph.
            var slotResult = call(client, "session.addSlot",
                    Map.of("params",
                            Map.of("sessionId", sessionId, "sectionId", sectionId, "title", "Kafka 101",
                                    "durationMinutes", 15, "presenters", List.of(PRESENTER_DISPLAY_NAME), "tags",
                                    List.of("kafka"))));
            assertThat(slotResult).contains(PRESENTER_DISPLAY_NAME);
            var slotId = JSON.readTree(slotResult).get("slotId").asLong();

            // Complete the loop: contribute a recording of her talk in the PRESENTER's name, tagged like the talk, and
            // file it against that exact slot (segmentId = slotId, per session.addSlot above).
            var typeId = seedArtifactType(ENTERPRISE_ORG, "E2E Backfill Recording");
            var contributeParams = new LinkedHashMap<String, Object>();
            contributeParams.put("slug", ENTERPRISE_SLUG);
            contributeParams.put("title", "Kafka 101 recording");
            contributeParams.put("typeId", typeId);
            contributeParams.put("url", "https://example.com/kafka-101.mp4");
            contributeParams.put("contributor", PRESENTER_DISPLAY_NAME);
            contributeParams.put("tags", List.of("kafka"));
            var artifactId = callForLong(client, "library.contribute", Map.of("params", contributeParams), "id");

            call(client, "session.attachArtifact", Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId, "segmentId",
                    slotId, "artifactId", artifactId));

            // ---------------------------------------------------------------
            // THE CLAIM. It is asserted here, before anything else this flow produced, and deliberately so: a
            // regression that credits the organizer is a broken promise to a person, and this test must say so in
            // those words rather than report a wrong row in a table nobody outside this module has heard of. The
            // mechanism-level assertions below are the how; this is the what.
            //
            // It is also read here, before the roster round trip: slot.addPresenter republishes a slot's presenters,
            // which would REPAIR a recordSlot that mis-credited its event and leave this blind to it.
            // ---------------------------------------------------------------
            assertTheGraphCreditsTheSpeakerAndNotTheOrganizer();

            // The mechanism beneath the claim. session.get's SlotView deliberately exposes only "open", hiding who
            // claimed a slot, so the attribution in the write model must be read straight from the rows.
            var presenterUserIds = jdbc.queryForList("SELECT user_id FROM event_slot_presenter WHERE slot_id = ?",
                    String.class, slotId);
            assertThat(presenterUserIds)
                    .as("the backfilled slot must be attributed to the seeded presenter, never to the token's owner")
                    .containsExactly(PRESENTER_USER).doesNotContain(ORGANIZER_USER);
            assertThat(jdbc.queryForList("SELECT tag FROM event_slot_tag WHERE slot_id = ?", String.class, slotId))
                    .as("a slot recorded with tags must carry them, or the talk teaches the expertise graph nothing")
                    .containsExactly("kafka");
            assertThat(jdbc.queryForObject("SELECT contributor_user_id FROM artifact WHERE id = ?", String.class,
                    artifactId)).as("the recording is the presenter's work, not the migrating organizer's")
                    .isEqualTo(PRESENTER_USER);

            var listedArtifacts = JSON.readTree(
                    call(client, "session.artifacts", Map.of("slug", ENTERPRISE_SLUG, "sessionId", sessionId)));
            var recordingRow = StreamSupport.stream(listedArtifacts.spliterator(), false)
                    .filter(row -> row.get("artifact").get("id").asLong() == artifactId).findFirst()
                    .orElseThrow(() -> new AssertionError("the recording must appear in session.artifacts"));
            assertThat(recordingRow.get("segmentId").asLong())
                    .as("the recording must be filed against the presented slot, not the whole session")
                    .isEqualTo(slotId);

            // HELD is asserted, not derived from the clock: a backfilled session scheduled in the past stays
            // SCHEDULED until somebody records that it happened.
            var session = call(client, "session.get", Map.of("id", sessionId));
            assertThat(JSON.readTree(session).get("status").asText()).isEqualTo("SCHEDULED");
            assertThat(session).contains("Kafka 101");

            // The loop the roster tool exists to close: a name the resolver cannot place is looked up, and the user id
            // it reports goes straight back in as a presenter reference and lands on exactly one member.
            rosterRoundTrip(client, new SlotCoords(sessionId, sectionId, slotId));

            // Naming a presenter who is already on the slot republishes it, so the graph is recomputed from the write
            // model. It must recompute to the same answer: an idempotent re-add credits nobody new, least of all the
            // organizer who made the call.
            assertTheGraphCreditsTheSpeakerAndNotTheOrganizer();
        } finally {
            close(client);
        }
    }

    /** The three ids that address one slot on the agent surface. Grouped so the helper below stays readable. */
    private record SlotCoords(Long sessionId, Long sectionId, Long slotId) {
    }

    /**
     * Reads the roster, then feeds the user id it reports straight back into {@code slot.addPresenter}. This is the
     * whole reason the roster tool exists: an agent holding a name the resolver cannot place looks the community up,
     * and the id it reads back must resolve to exactly one member, with nothing left ambiguous. Re-naming a presenter
     * who is already on the slot changes nothing, which is what makes the loop safe to close mid-flow.
     */
    private void rosterRoundTrip(McpSyncClient client, SlotCoords slot) {

        var roster = call(client, "community.members", Map.of("slug", ENTERPRISE_SLUG));
        assertThat(roster).as("the roster must name the speaker, and must never carry her email")
                .contains(PRESENTER_DISPLAY_NAME).doesNotContain("email").doesNotContain("@")
                .doesNotContain(PRESENTER_EMAIL);

        var presenterId = rosterUserIdOf(roster, PRESENTER_DISPLAY_NAME);
        assertThat(presenterId).as("the roster must report the id an agent can act on").isEqualTo(PRESENTER_USER);

        var text = call(client, "slot.addPresenter", Map.of("sessionId", slot.sessionId(), "sectionId",
                slot.sectionId(), "slotId", slot.slotId(), "presenter", presenterId));
        var assignment = JSON.readTree(text);
        assertThat(assignment.get("assigned").asBoolean())
                .as("a user id read from the roster must resolve to exactly one member: %s", text).isTrue();
        assertThat(assignment.get("candidates").asInt()).as("an exact id match leaves nothing to disambiguate")
                .isZero();
        assertThat(text).as("a roster id is neither unplaceable nor ambiguous").doesNotContain("NOT_FOUND")
                .doesNotContain("AMBIGUOUS");
    }

    /**
     * The load-bearing assertion of the feature, read through the expertise graph's real read path rather than out of
     * the {@code expertise_signal} table: a projection row nobody can see is not expertise.
     *
     * <p>
     * PRESENTED is what makes a PRACTITIONER; a CONTRIBUTED signal alone would only make a CONTRIBUTOR, so the tier
     * assertion below is what proves the SLOT was attributed, not merely the artifact. The organizer's own profile is
     * then read from the other side, org-wide and across every area, because "not at all" means nowhere, not merely
     * "not under kafka".
     */
    private void assertTheGraphCreditsTheSpeakerAndNotTheOrganizer() {

        var practitioners = asUser(ENTERPRISE_USER,
                () -> expertise.whoKnows(ENTERPRISE_SLUG, "kafka", /* demonstratedOnly */ false));

        assertThat(practitioners).extracting(Practitioner::userId)
                .as("the speaker must be in the graph, and the organizer who backfilled her talk must not")
                .contains(PRESENTER_USER).doesNotContain(ORGANIZER_USER);

        var speaker = practitioners.stream().filter(p -> PRESENTER_USER.equals(p.userId())).findFirst().orElseThrow();
        assertThat(speaker.tier())
                .as("presenting is what makes a practitioner: the recording alone would say only "
                        + "CONTRIBUTOR, so this is what proves the SLOT was credited to her")
                .isEqualTo(Tier.PRACTITIONER);
        // These three counts are absolutes over a fixture this class only cleans after the last test, so they hold an
        // invariant on the class as a whole: no other test here may produce an expertise signal for the presenter or
        // the organizer. If one ever does, these lines break for a reason unrelated to the claim, and the tempting fix
        // is to relax them into "at least one". Do not. Give the new test its own users instead.
        assertThat(speaker.evidence().presentedCount()).as("the talk she gave").isEqualTo(1);
        assertThat(speaker.evidence().contributedCount()).as("the recording of it, filed in her name").isEqualTo(1);

        var organizer = asUser(ORGANIZER_USER, () -> expertiseProfile.selfView());
        assertThat(organizer.optedOut()).as("the organizer's absence must be a real absence, not an opt-out hiding "
                + "signals the backfill wrongly created").isFalse();
        assertThat(organizer.areas())
                .as("an organizer who migrates somebody else's talk earns no expertise of their own, in any area")
                .isEmpty();
    }

    /** The user id the roster reports for a display name: exactly what an agent would feed back as a reference. */
    private static String rosterUserIdOf(String rosterJson, String displayName) {

        return StreamSupport.stream(JSON.readTree(rosterJson).spliterator(), false)
                .filter(row -> displayName.equals(row.get("displayName").asString()))
                .map(row -> row.get("userId").asString()).findFirst()
                .orElseThrow(() -> new AssertionError("the roster must carry " + displayName));
    }

    /**
     * Runs a read as an authenticated user of the enterprise org. The expertise graph is a REST-chain read model: it
     * takes its caller and its Organization off the JWT in the SecurityContext, exactly as it does behind
     * {@code /axonia/**}. The {@code axp_} token drives the MCP surface; this drives the surface a human would.
     */
    private static <T> T asUser(String userId, Supplier<T> read) {

        var jwt = Jwt.withTokenValue("e2e-" + userId).header("alg", "none").subject(userId).claim("org", ENTERPRISE_ORG)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        try {
            return read.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 11: community.members lists the roster (names, never emails), and read does not confer members:read.
    // -------------------------------------------------------------------------

    @Test
    void communityMembers_listsTheRosterWithNamesAndNoEmails() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("members:read"));
        var client = connect(bearer);
        try {
            client.initialize();

            var roster = call(client, "community.members", Map.of("slug", ENTERPRISE_SLUG));

            assertThat(roster).as("the roster must carry the display name an unplaceable reference is matched against")
                    .contains(PRESENTER_DISPLAY_NAME).contains(PRESENTER_USER);
            // PRESENTER_USER really has an email in the directory, so this can fail: doesNotContain("@") catches a
            // leaked value, and doesNotContain("email") catches a leaked field name even when the value is null.
            assertThat(roster).as("an email is the one field that turns a roster into a phishing list")
                    .doesNotContain("email").doesNotContain("@").doesNotContain(PRESENTER_EMAIL);
        } finally {
            close(client);
        }
    }

    @Test
    void readScopeDoesNotConferMembersRead() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read"));
        var client = connect(bearer);
        try {
            client.initialize();
            var result = client
                    .callTool(new McpSchema.CallToolRequest("community.members", Map.of("slug", ENTERPRISE_SLUG)));

            assertThat(result.isError()).as("community.members without members:read must be an error result")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(result)).as("the refusal must name the missing scope").contains("members:read");
            assertThat(auditOutcomes(ENTERPRISE_ORG, ENTERPRISE_USER, "community.members"))
                    .as("a SCOPE_DENIED audit row for community.members must exist").contains("SCOPE_DENIED");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 12: a members:read token whose owner is not a member of the community is refused the roster.
    // -------------------------------------------------------------------------

    @Test
    void nonMemberWithMembersReadScope_isRefusedTheRoster() {
        // The scope is present, so this is not the scope gate: it is the domain floor underneath it. A token cannot
        // give its agent sight of a roster its own owner may not see.
        var bearer = mint(ENTERPRISE_ORG, NON_MEMBER_USER, Set.of("members:read"));
        var client = connect(bearer);
        try {
            client.initialize();
            var result = client
                    .callTool(new McpSchema.CallToolRequest("community.members", Map.of("slug", ENTERPRISE_SLUG)));

            assertThat(result.isError()).as("a non-member must be refused the roster even with members:read")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(result)).as("not one name may escape the refusal").doesNotContain(PRESENTER_USER)
                    .doesNotContain(PRESENTER_DISPLAY_NAME);
            assertThat(auditOutcomes(ENTERPRISE_ORG, NON_MEMBER_USER, "community.members"))
                    .as("the refusal is a domain refusal, not a scope one, and is audited as such")
                    .containsExactly("DOMAIN_ERROR");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 13: the roster's pages neither repeat nor skip a member.
    // -------------------------------------------------------------------------

    @Test
    void rosterPages_neitherRepeatNorSkipAMember() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("members:read"));
        var client = connect(bearer);
        try {
            client.initialize();

            var first = memberIds(call(client, "community.members", Map.of("slug", ROSTER_SLUG, "page", 0, "size", 2)));
            var second = memberIds(
                    call(client, "community.members", Map.of("slug", ROSTER_SLUG, "page", 1, "size", 2)));

            assertThat(first).as("page 2 must not repeat a member of page 1").doesNotContainAnyElementsOf(second);
            // Ascending by user id, which the seed order deliberately is not: an ORDER BY that Spring Data resolved
            // to nothing and silently dropped would hand back insertion order and fail here, and a roster paged with
            // no order at all can hand the same member back twice while never showing another at all.
            assertThat(Stream.concat(first.stream(), second.stream()).toList())
                    .as("the two pages together must be the whole roster, in the stable order the surface pins")
                    .containsExactlyElementsOf(ROSTER_ASCENDING);
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 14: a plain member crediting somebody else is told to drop the field, not that it is a stranger.
    // -------------------------------------------------------------------------

    @Test
    void plainMemberCreditingAnotherMember_isToldToOmitTheField_notThatItIsNotAMember() {
        // ENTERPRISE_USER is a plain MEMBER of ENTERPRISE_SLUG and the token carries both scopes the calls need, so
        // neither refusal below is a scope refusal: this is the domain's Credit contributions / organizer check,
        // reachable at all only because contributor/proposer now exist on the surface. An agent crediting
        // somebody else lands here, and the one thing it must not be told is that it is not a member: it is, and
        // community.join would be its rational, useless next move.
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("library:contribute", "topic:propose"));
        var typeId = seedArtifactType(ENTERPRISE_ORG, "E2E Attribution Refusal");
        var before = artifactCount(ENTERPRISE_ORG);
        var client = connect(bearer);
        try {
            client.initialize();

            var contributeParams = new LinkedHashMap<String, Object>();
            contributeParams.put("slug", ENTERPRISE_SLUG);
            contributeParams.put("title", "Not mine to credit");
            contributeParams.put("typeId", typeId);
            contributeParams.put("url", "https://example.test/attribution");
            contributeParams.put("contributor", PRESENTER_DISPLAY_NAME);
            var contributed = client
                    .callTool(new McpSchema.CallToolRequest("library.contribute", Map.of("params", contributeParams)));

            assertThat(contributed.isError()).as("crediting another member as a plain member must be refused")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(contributed)).as("the refusal must name the field to drop, and the act it enables")
                    .contains("Only an organizer, or a member the community lets credit contributions, may credit a "
                            + "contribution to another member.")
                    .contains("Omit contributor to record this contribution as your own.")
                    .doesNotContain("not a member");
            assertThat(artifactCount(ENTERPRISE_ORG)).as("a refused contribution must store nothing").isEqualTo(before);

            var proposeParams = new LinkedHashMap<String, Object>();
            proposeParams.put("slug", ENTERPRISE_SLUG);
            proposeParams.put("kind", "OFFER");
            proposeParams.put("title", "Not mine to propose");
            proposeParams.put("proposer", PRESENTER_DISPLAY_NAME);
            var proposed = client
                    .callTool(new McpSchema.CallToolRequest("topic.propose", Map.of("params", proposeParams)));

            assertThat(proposed.isError()).as("proposing in another member's name as a plain member must be refused")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(proposed)).as("the same rule, worded for the field the caller actually supplied")
                    .contains("Only an organizer may credit a proposal to another member.")
                    .contains("Omit proposer to record this proposal as your own.").doesNotContain("not a member");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 15: an unplaceable name refuses the write, and says which name and why.
    // -------------------------------------------------------------------------

    @Test
    void unplaceableContributorOrProposer_refusesTheWrite_andSaysWhichNameAndWhy() {
        // The organizer check passes here, so what is under test is the layer behind it: the same sanitizer that used
        // to flatten the organizer refusal must NOT flatten these, or an agent holding a name it cannot place is told
        // only "the action could not be completed" and has nothing to fix.
        var bearer = mint(ENTERPRISE_ORG, ORGANIZER_USER, Set.of("library:contribute", "topic:propose"));
        var typeId = seedArtifactType(ENTERPRISE_ORG, "E2E Unplaceable Name");
        var before = artifactCount(ENTERPRISE_ORG);
        var client = connect(bearer);
        try {
            client.initialize();

            var notFound = client.callTool(new McpSchema.CallToolRequest("library.contribute",
                    Map.of("params", contributeArgs(typeId, "Nobody At All"))));
            assertThat(notFound.isError()).as("a name matching nobody must refuse the write").isEqualTo(Boolean.TRUE);
            assertThat(textOf(notFound)).as("the agent must learn which name failed, or it cannot fix it")
                    .contains("No member of this community matches: Nobody At All")
                    .doesNotContain("could not be completed");

            // "Agent" token-matches both ENTERPRISE_USER and ORGANIZER_USER (their seeded display names both start
            // with it) and exactly-matches neither, which is the AMBIGUOUS case.
            var ambiguous = client.callTool(new McpSchema.CallToolRequest("library.contribute",
                    Map.of("params", contributeArgs(typeId, "Agent"))));
            assertThat(ambiguous.isError()).as("a name matching several members must refuse the write")
                    .isEqualTo(Boolean.TRUE);
            assertThat(textOf(ambiguous)).as("how many it matched is what tells the agent to send a fuller name")
                    .contains("members of this community match: Agent").doesNotContain("could not be completed");

            assertThat(artifactCount(ENTERPRISE_ORG)).as("neither refusal may credit the caller by default")
                    .isEqualTo(before);

            // The proposer side refuses identically, and is asserted rather than assumed: the two resolvers are
            // deliberate near-duplicates that CPD cannot see, so nothing but a test keeps them saying the same thing.
            var proposerNotFound = client.callTool(
                    new McpSchema.CallToolRequest("topic.propose", Map.of("params", proposeArgs("Nobody At All"))));
            assertThat(proposerNotFound.isError()).isEqualTo(Boolean.TRUE);
            assertThat(textOf(proposerNotFound)).contains("No member of this community matches: Nobody At All")
                    .doesNotContain("could not be completed");

            var proposerAmbiguous = client
                    .callTool(new McpSchema.CallToolRequest("topic.propose", Map.of("params", proposeArgs("Agent"))));
            assertThat(proposerAmbiguous.isError()).isEqualTo(Boolean.TRUE);
            assertThat(textOf(proposerAmbiguous)).contains("members of this community match: Agent")
                    .doesNotContain("could not be completed");
        } finally {
            close(client);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion 16: the learning round trip - add, read it back as held, remove.
    // -------------------------------------------------------------------------

    @Test
    void learningRoundTrip_addedAreaIsListedHeld_thenRemovedFromTheDemand() {
        var bearer = mint(ENTERPRISE_ORG, ENTERPRISE_USER, Set.of("read", "learning:declare"));
        var client = connect(bearer);
        try {
            client.initialize();

            var added = JSON.readTree(call(client, "learning.add", Map.of("slug", ENTERPRISE_SLUG, "area", "Kafka")));
            assertThat(added.get("tag").asString()).as("the area comes back as the community reads it")
                    .isEqualTo("kafka");

            var kafka = demandFor(client).stream().filter(row -> "kafka".equals(row.get("tag").asString())).findFirst()
                    .orElseThrow(() -> new AssertionError("learning.demand must list kafka"));
            assertThat(kafka.get("learners").asInt()).isEqualTo(1);
            assertThat(kafka.get("held").asBoolean()).as("the caller's own area is marked held").isTrue();

            var removed = JSON
                    .readTree(call(client, "learning.remove", Map.of("slug", ENTERPRISE_SLUG, "area", "KAFKA")));
            assertThat(removed.get("tag").asString()).as("the removed area comes back as the community reads it")
                    .isEqualTo("kafka");

            assertThat(demandFor(client)).extracting(row -> row.get("tag").asString())
                    .as("nobody wants kafka once its only learner removed it").doesNotContain("kafka");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM learning_intent WHERE organization_id = ? " + "AND user_id = ?", Long.class,
                    ENTERPRISE_ORG, ENTERPRISE_USER)).isZero();
            assertThat(auditOutcomes(ENTERPRISE_ORG, ENTERPRISE_USER, "learning.remove")).containsExactly("OK");
        } finally {
            close(client);
        }
    }

    private static List<JsonNode> demandFor(McpSyncClient client) {
        var text = call(client, "learning.demand", Map.of("slug", ENTERPRISE_SLUG));
        return StreamSupport.stream(JSON.readTree(text).spliterator(), false).toList();
    }

    /** The contribute arguments, varying only the contributor reference under test. */
    private static Map<String, Object> contributeArgs(Long typeId, String contributor) {
        var params = new LinkedHashMap<String, Object>();
        params.put("slug", ENTERPRISE_SLUG);
        params.put("title", "Credited to " + contributor);
        params.put("typeId", typeId);
        params.put("url", "https://example.test/unplaceable");
        params.put("contributor", contributor);
        return params;
    }

    /** The propose arguments, varying only the proposer reference under test. */
    private static Map<String, Object> proposeArgs(String proposer) {
        var params = new LinkedHashMap<String, Object>();
        params.put("slug", ENTERPRISE_SLUG);
        params.put("kind", "OFFER");
        params.put("title", "Proposed for " + proposer);
        params.put("proposer", proposer);
        return params;
    }

    // -------------------------------------------------------------------------

    /** Builds a synchronous MCP client over the streamable-HTTP transport, attaching the bearer to every request. */
    private McpSyncClient connect(String bearer) {
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/mcp")
                .httpRequestCustomizer(
                        (builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + bearer))
                .build();
        return McpClient.sync(transport).clientInfo(new McpSchema.Implementation("axonia-agent-e2e", "1.0.0")).build();
    }

    private static List<String> toolNames(McpSyncClient client) {
        return client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
    }

    /**
     * Calls {@code topic.propose} in the confirmed NESTED shape (its {@code @McpToolParam ProposeTopicParams} record
     * groups the arguments under {@code params}), supplying only the REQUIRED fields (slug, kind, title) and omitting
     * the now-optional description/tags, which also proves those two fields are no longer required.
     */
    private static McpSchema.CallToolResult proposeTopic(McpSyncClient client, String slug) {
        var params = new LinkedHashMap<String, Object>();
        params.put("slug", slug);
        params.put("kind", "REQUEST");
        params.put("title", "Agent-proposed topic");
        return client.callTool(new McpSchema.CallToolRequest("topic.propose", Map.of("params", params)));
    }

    private static String textOf(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(c -> ((McpSchema.TextContent) c).text()).collect(Collectors.joining(" "));
    }

    /** Calls a tool, asserts it succeeded, and returns its raw text content for assertions or field extraction. */
    private static String call(McpSyncClient client, String tool, Map<String, Object> args) {
        var result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        var text = textOf(result);
        assertThat(result.isError()).as("%s must succeed: %s", tool, text).isNotEqualTo(Boolean.TRUE);
        return text;
    }

    /**
     * Calls a tool and reads a numeric field back out of its JSON result, e.g. {@code sessionId} or {@code sectionId}.
     */
    private static Long callForLong(McpSyncClient client, String tool, Map<String, Object> args, String field) {
        var text = call(client, tool, args);
        return JSON.readTree(text).get(field).asLong();
    }

    /** The user ids of one {@code community.members} page, in the order the tool returned them. */
    private static List<String> memberIds(String rosterJson) {
        return StreamSupport.stream(JSON.readTree(rosterJson).spliterator(), false)
                .map(row -> row.get("userId").asString()).toList();
    }

    /** Reads the id of the first section type back from {@code events.sectionTypes}, for use as a sectionTypeId. */
    private static Long firstSectionTypeId(McpSyncClient client) {
        var text = call(client, "events.sectionTypes", Map.of());
        return JSON.readTree(text).get(0).get("id").asLong();
    }

    private static void close(McpSyncClient client) {
        client.closeGracefully();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // -------------------------------------------------------------------------
    // Token + seeding helpers
    // -------------------------------------------------------------------------

    private String mint(String org, String user, Set<String> scopes) {
        return tokens.mint(new MintPersonalAccessToken(user, org, "e2e-" + user, scopes, null));
    }

    private void seedActiveMember(String org, String userId) {
        seedActiveMember(org, userId, "Agent " + userId, null);
    }

    private void seedActiveMember(String org, String userId, String displayName, String email) {
        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival, "
                        + "created_at, updated_at, version) VALUES (?, ?, ?, ?, true, ?, 'SYNCED', now(), now(), 0)",
                org, userId, displayName, email, "scim-" + userId);
    }

    /** Adds {@code userId} as a plain MEMBER of an already-existing community, without creating the community. */
    private void seedMembership(String org, String slug, String userId) {
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'MEMBER', now(), now(), 0)", org, communityId(org, slug), userId);
    }

    private void promoteToOrganizer(String org, String slug, String userId) {
        jdbc.update("UPDATE memberships SET role = 'ORGANIZER' WHERE organization_id = ? AND community_id = ? "
                + "AND user_id = ?", org, communityId(org, slug), userId);
    }

    /**
     * Seeds an ENTERPRISE-kind organization. Upserts rather than inserts because another class committing in the same
     * run may already have created the row. The agent surface is Enterprise-only, so every organization this test seeds
     * is ENTERPRISE, including the second one assertion 7 acts in: kind is no longer what distinguishes them, only that
     * they are independently seeded.
     */
    private void seedEnterpriseOrganization(String org) {
        jdbc.update("INSERT INTO organization (id, name, kind, created_at, updated_at) "
                + "VALUES (?, ?, 'ENTERPRISE', now(), now()) " + "ON CONFLICT (id) DO UPDATE SET kind = 'ENTERPRISE'",
                org, "Agent Enterprise");
    }

    private Long insertCommunityKind(String org) {
        return jdbc.queryForObject("INSERT INTO community_kind (organization_id, label, created_at, updated_at, "
                + "version) VALUES (?, 'Guild', now(), now(), 0) RETURNING id", Long.class, org);
    }

    private Long insertSectionType(String org) {
        return jdbc.queryForObject(
                "INSERT INTO event_section_type (organization_id, label, expects_presenter, "
                        + "icon, accent_color, created_at, updated_at, version) "
                        + "VALUES (?, 'Talks', true, 'wrench', '#123456', now(), now(), 0) RETURNING id",
                Long.class, org);
    }

    private void seedCommunityWithKind(String org, String slug, String userId, Long kindId) {
        var communityId = jdbc.queryForObject(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', ?, now(), now(), 0) RETURNING id",
                Long.class, org, slug, slug, kindId);
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, ?, ?, 'MEMBER', now(), now(), 0)", org, communityId, userId);
    }

    private long topicCount(String org) {
        return jdbc.queryForObject("SELECT count(*) FROM topic WHERE organization_id = ?", Long.class, org);
    }

    private long artifactCount(String org) {
        return jdbc.queryForObject("SELECT count(*) FROM artifact WHERE organization_id = ?", Long.class, org);
    }

    private List<String> auditOutcomes(String org, String userId, String toolName) {
        return jdbc.queryForList(
                "SELECT outcome FROM agent_tool_call WHERE organization_id = ? AND user_id = ? " + "AND tool_name = ?",
                String.class, org, userId, toolName);
    }

    private Long communityId(String org, String slug) {
        return jdbc.queryForObject("SELECT id FROM communities WHERE organization_id = ? AND slug = ?", Long.class, org,
                slug);
    }

    /**
     * Seeds a session in the given status. {@code status} decides which arm of
     * {@code ResourceAttachAuthorizer.requireScheduleManagerUnlessHeld} an attach against it exercises: HELD opens
     * attach to any member, SCHEDULED (not yet held) keeps it schedule-management-gated.
     */
    private Long seedSession(String org, String slug, String status) {
        return jdbc.queryForObject(
                "INSERT INTO community_session (organization_id, community_id, status, scheduled_at, "
                        + "duration_minutes, notes, recording_url, has_time, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, now(), 60, '', '', true, now(), now(), 0) RETURNING id",
                Long.class, org, communityId(org, slug), status);
    }

    /**
     * The label must not collide with the defaults every organization is seeded with
     * (uq_artifact_type_organization_label).
     */
    private Long seedArtifactType(String org) {
        return seedArtifactType(org, "E2E Session Recording");
    }

    /** Overload for callers that need a second, distinctly labeled artifact type in the same organization. */
    private Long seedArtifactType(String org, String label) {
        return jdbc.queryForObject("INSERT INTO artifact_type (organization_id, label, created_at, updated_at, "
                + "version) VALUES (?, ?, now(), now(), 0) RETURNING id", Long.class, org, label);
    }

    private Long artifactIdByTitle(String org, String title) {
        return jdbc.queryForObject("SELECT id FROM artifact WHERE organization_id = ? AND title = ?", Long.class, org,
                title);
    }

    private long attachmentCount(String org, Long artifactId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM artifact_attachment WHERE organization_id = ? AND artifact_id = ?", Long.class,
                org, artifactId);
    }
}
