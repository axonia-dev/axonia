package dev.axonia.agent.adapter.in.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
import dev.axonia.identity.api.pat.MintPersonalAccessToken;
import dev.axonia.identity.api.pat.PersonalAccessTokens;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The tag curation surface end to end: real {@code axp_} tokens against the {@code /mcp} endpoint of the booted
 * application, over the Testcontainers Postgres, so every tool runs through scope check, curator check, the expertise
 * services and the stores exactly as an agent reaches them.
 *
 * <p>
 * The claim it proves is the acceptance of the feature: an agent with only MCP access lists a community's tags, aliases
 * and named areas, reviews and acts on suggested merges, merges, splits and renames tags, builds named areas several
 * tags at a time, moves a tag between them, and is refused all of it when its owner is not a curator. Along the way the
 * tagging tools say which tags they created and which they used that a curator had merged away.
 */
@CoreSpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("DEV")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AgentTagCurationEndToEndTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String ORG = "agent-tags-e2e";
    private static final String SLUG = "agent-tags-guild";
    private static final String REFUSAL_SLUG = "agent-tags-refusals";

    /** An organizer, and so a curator: a community opens tag curation to its organizers only, by default. */
    private static final String CURATOR = "agent-tags-curator";

    /** A plain member, whom no community's default lets curate. */
    private static final String MEMBER = "agent-tags-member";

    private static final AtomicBoolean SEEDED = new AtomicBoolean(false);

    @LocalServerPort
    private int port;

    @Autowired
    private PersonalAccessTokens tokens;

    @Autowired
    private JdbcTemplate jdbc;

    /** The REST chain needs a decoder bean; an {@code axp_} token never reaches it. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Long typeId;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("not a JWT"));

        if (SEEDED.compareAndSet(false, true)) {
            jdbc.update("INSERT INTO organization (id, name, kind, created_at, updated_at) "
                    + "VALUES (?, 'Agent Tags', 'ENTERPRISE', now(), now()) "
                    + "ON CONFLICT (id) DO UPDATE SET kind = 'ENTERPRISE'", ORG);
            var kindId = jdbc.queryForObject(
                    "INSERT INTO community_kind (organization_id, label, created_at, "
                            + "updated_at, version) VALUES (?, 'Guild', now(), now(), 0) RETURNING id",
                    Long.class, ORG);
            jdbc.queryForObject("INSERT INTO artifact_type (organization_id, label, created_at, updated_at, version) "
                    + "VALUES (?, 'E2E Tag Slides', now(), now(), 0) RETURNING id", Long.class, ORG);
            seedUser(CURATOR);
            seedUser(MEMBER);
            for (var slug : List.of(SLUG, REFUSAL_SLUG)) {
                seedCommunity(slug, kindId);
                seedMembership(slug, CURATOR, "ORGANIZER");
                seedMembership(slug, MEMBER, "MEMBER");
            }
        }
        typeId = jdbc.queryForObject("SELECT id FROM artifact_type WHERE organization_id = ? AND label = ?", Long.class,
                ORG, "E2E Tag Slides");
    }

    @Test
    void aCuratorKeepsTheVocabularySmall_throughEveryTagAndNamedAreaTool() {
        var client = connect(mint(CURATOR, Set.of("read", "library:contribute", "tag:curate")));
        try {
            client.initialize();

            // given - an import that tagged its artifacts freely, and was told so
            var first = contribute(client, SLUG, "Cluster basics", List.of("kubernetes", "k8s", "kafka"));
            assertThat(strings(first.get("tagReport").get("created")))
                    .as("a fresh community carries none of the tags, so the call created all of them")
                    .containsExactlyInAnyOrder("kubernetes", "k8s", "kafka");
            var second = contribute(client, SLUG, "Kube tips", List.of("kube", "spring-boot", "kafka"));
            assertThat(strings(second.get("tagReport").get("created"))).as("kafka already existed")
                    .containsExactlyInAnyOrder("kube", "spring-boot");

            // then - the listing tools show the sprawl
            assertThat(vocabulary(client).keySet()).containsExactlyInAnyOrder("kafka", "k8s", "kube", "kubernetes",
                    "spring-boot");
            assertThat(read(client, "namedArea.list", Map.of("slug", SLUG))).isEmpty();

            // when - the agent reviews the suggested merge and parks it, then thinks again
            var suggestion = onlySuggestion(client);
            assertThat(
                    Set.of(suggestion.get("tagA").get("tag").asString(), suggestion.get("tagB").get("tag").asString()))
                    .containsExactlyInAnyOrder("kube", "kubernetes");
            assertThat(suggestion.get("similarity").asDouble()).isGreaterThanOrEqualTo(0.7);
            assertThat(usageOf(suggestion, "kubernetes").get("artifacts").asInt())
                    .as("each side says what it holds up, so the agent can judge the pair").isEqualTo(1);
            call(client, "tag.dismissSuggestion", Map.of("slug", SLUG, "tagA", "kube", "tagB", "kubernetes"));
            assertThat(read(client, "tag.suggestions", Map.of("slug", SLUG))).as("a parked pair is not suggested")
                    .isEmpty();
            call(client, "tag.undismissSuggestion", Map.of("slug", SLUG, "tagA", "kube", "tagB", "kubernetes"));
            assertThat(read(client, "tag.suggestions", Map.of("slug", SLUG))).hasSize(1);

            // when - it merges both spellings in one call
            var merged = read(client, "tag.merge",
                    Map.of("slug", SLUG, "variants", List.of("k8s", "kube"), "canonical", "kubernetes"));

            // then - the answer and the vocabulary both show one tag with two variants
            assertThat(merged.get("tag").asString()).isEqualTo("kubernetes");
            assertThat(strings(merged.get("variants"))).containsExactly("k8s", "kube");
            var afterMerge = vocabulary(client);
            assertThat(afterMerge.keySet()).containsExactlyInAnyOrder("kafka", "kubernetes", "spring-boot");
            assertThat(strings(afterMerge.get("kubernetes").get("variants"))).containsExactly("k8s", "kube");
            assertThat(read(client, "tag.suggestions", Map.of("slug", SLUG))).as("a merged spelling is not a candidate")
                    .isEmpty();

            // when - a later import uses a merged spelling and a new tag
            var third = contribute(client, SLUG, "Cluster upgrades", List.of("k8s", "terraform"));

            // then - it is told which tag is new and which reads as another
            assertThat(strings(third.get("tagReport").get("created"))).containsExactly("terraform");
            var mergedTag = third.get("tagReport").get("merged").get(0);
            assertThat(mergedTag.get("tag").asString()).isEqualTo("k8s");
            assertThat(mergedTag.get("canonical").asString()).isEqualTo("kubernetes");

            // when - one spelling is split back out
            var split = read(client, "tag.split", Map.of("slug", SLUG, "variant", "k8s"));

            // then - both tags come back as they now are
            assertThat(split.get("variant").get("tag").asString()).isEqualTo("k8s");
            assertThat(split.get("formerCanonical").get("tag").asString()).isEqualTo("kubernetes");
            assertThat(strings(split.get("formerCanonical").get("variants"))).containsExactly("kube");
            assertThat(vocabulary(client)).containsKey("k8s");

            // when - a tag is renamed
            var renamed = read(client, "tag.rename", Map.of("slug", SLUG, "from", "spring-boot", "to", "Spring"));

            // then - the content carries the new name
            assertThat(renamed.get("tag").asString()).isEqualTo("spring");
            assertThat(tagsOfArtifact("Kube tips")).containsExactlyInAnyOrder("kube", "spring", "kafka");
            assertThat(vocabulary(client)).containsKey("spring").doesNotContainKey("spring-boot");

            // when - two named areas are built, one of them several tags at once
            var messagingId = read(client, "namedArea.create", Map.of("slug", SLUG, "name", "Messaging")).get("id")
                    .asLong();
            var platform = read(client, "namedArea.create", Map.of("slug", SLUG, "name", "Platform"));
            assertThat(platform.get("tags")).isEmpty();
            var platformId = platform.get("id").asLong();
            var messaging = read(client, "namedArea.assignTags",
                    Map.of("slug", SLUG, "id", messagingId, "tags", List.of("kafka")));
            assertThat(messaging.get("moved")).isEmpty();
            var assigned = read(client, "namedArea.assignTags",
                    Map.of("slug", SLUG, "id", platformId, "tags", List.of("kubernetes", "kafka", "terraform")));

            // then - the area holds all three, and the move out of Messaging is reported
            assertThat(strings(assigned.get("area").get("tags"))).containsExactly("kafka", "kubernetes", "terraform");
            var moved = assigned.get("moved").get(0);
            assertThat(assigned.get("moved")).hasSize(1);
            assertThat(moved.get("tag").asString()).isEqualTo("kafka");
            assertThat(moved.get("fromAreaId").asLong()).isEqualTo(messagingId);
            assertThat(moved.get("fromAreaName").asString()).isEqualTo("Messaging");
            assertThat(areaTags(client)).containsEntry("Messaging", List.of()).containsEntry("Platform",
                    List.of("kafka", "kubernetes", "terraform"));
            var kafka = vocabulary(client).get("kafka");
            assertThat(kafka.get("namedArea").get("name").asString()).as("the vocabulary names the area each tag is in")
                    .isEqualTo("Platform");

            // when - a tag is taken out, an area renamed and the emptied one deleted
            var unassigned = read(client, "namedArea.unassignTag", Map.of("slug", SLUG, "tag", "terraform"));
            var renamedArea = read(client, "namedArea.rename",
                    Map.of("slug", SLUG, "id", platformId, "name", "Platform engineering"));
            var deleted = read(client, "namedArea.delete", Map.of("slug", SLUG, "id", messagingId));

            // then
            assertThat(strings(unassigned.get("fromArea").get("tags"))).containsExactly("kafka", "kubernetes");
            assertThat(strings(renamedArea.get("tags"))).as("a renamed area keeps its tags").containsExactly("kafka",
                    "kubernetes");
            assertThat(deleted.get("name").asString()).isEqualTo("Messaging");
            assertThat(areaTags(client)).containsOnlyKeys("Platform engineering");
            assertThat(vocabulary(client)).as("an area's tags outlive it").containsKey("terraform");
        } finally {
            client.closeGracefully();
        }
    }

    @Test
    void aMemberWhoCannotCurate_isRefusedEveryCurationTool_andChangesNothing() {
        var curator = connect(mint(CURATOR, Set.of("read", "library:contribute")));
        var member = connect(mint(MEMBER, Set.of("read", "tag:curate")));
        try {
            curator.initialize();
            member.initialize();
            contribute(curator, REFUSAL_SLUG, "Streams", List.of("kafka", "kafka-streams"));

            // a plain member reads the tags to reuse, but nothing a curator decides
            assertThat(strings(read(member, "tag.list", Map.of("slug", REFUSAL_SLUG)))).containsExactly("kafka",
                    "kafka-streams");
            var slug = Map.<String, Object>of("slug", REFUSAL_SLUG);
            var pair = Map.<String, Object>of("slug", REFUSAL_SLUG, "tagA", "kafka", "tagB", "kafka-streams");
            var refusals = Map.<String, Map<String, Object>>ofEntries(Map.entry("tag.vocabulary", slug),
                    Map.entry("tag.suggestions", slug), Map.entry("namedArea.list", slug),
                    Map.entry("tag.merge",
                            Map.of("slug", REFUSAL_SLUG, "variants", List.of("kafka-streams"), "canonical", "kafka")),
                    Map.entry("tag.split", Map.of("slug", REFUSAL_SLUG, "variant", "kafka-streams")),
                    Map.entry("tag.rename", Map.of("slug", REFUSAL_SLUG, "from", "kafka", "to", "streaming")),
                    Map.entry("tag.dismissSuggestion", pair), Map.entry("tag.undismissSuggestion", pair),
                    Map.entry("namedArea.create", Map.of("slug", REFUSAL_SLUG, "name", "Messaging")),
                    Map.entry("namedArea.rename", Map.of("slug", REFUSAL_SLUG, "id", 1L, "name", "Streaming")),
                    Map.entry("namedArea.delete", Map.of("slug", REFUSAL_SLUG, "id", 1L)),
                    Map.entry("namedArea.assignTags", Map.of("slug", REFUSAL_SLUG, "id", 1L, "tags", List.of("kafka"))),
                    Map.entry("namedArea.unassignTag", Map.of("slug", REFUSAL_SLUG, "tag", "kafka")));
            refusals.forEach((tool, args) -> assertThat(refusal(member, tool, args)).as(tool)
                    .contains("not permitted to do this"));

            assertThat(tagsOfArtifact("Streams")).as("nothing was merged or renamed").containsExactlyInAnyOrder("kafka",
                    "kafka-streams");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM expertise_named_area WHERE organization_id = ? AND community_id = "
                            + "(SELECT id FROM communities WHERE organization_id = ? AND slug = ?)",
                    Long.class, ORG, ORG, REFUSAL_SLUG)).as("no named area was created").isZero();
        } finally {
            curator.closeGracefully();
            member.closeGracefully();
        }
    }

    @Test
    void aCuratorsTokenWithoutTheCurateScope_isRefusedTheWrites_evenWithTagDelete() {
        var client = connect(mint(CURATOR, Set.of("read", "tag:delete")));
        try {
            client.initialize();

            assertThat(refusal(client, "tag.merge",
                    Map.of("slug", REFUSAL_SLUG, "variants", List.of("kafka-streams"), "canonical", "kafka")))
                    .contains("tag:curate");
            assertThat(refusal(client, "namedArea.create", Map.of("slug", REFUSAL_SLUG, "name", "Messaging")))
                    .contains("tag:curate");
        } finally {
            client.closeGracefully();
        }
    }

    private JsonNode contribute(McpSyncClient client, String slug, String title, List<String> tags) {
        var params = new LinkedHashMap<String, Object>();
        params.put("slug", slug);
        params.put("title", title);
        params.put("typeId", typeId);
        params.put("url", "https://example.com/" + title.toLowerCase().replace(' ', '-'));
        params.put("tags", tags);
        return read(client, "library.contribute", Map.of("params", params));
    }

    /** The vocabulary keyed by canonical tag. */
    private static Map<String, JsonNode> vocabulary(McpSyncClient client) {
        return nodes(read(client, "tag.vocabulary", Map.of("slug", SLUG))).stream()
                .collect(Collectors.toMap(entry -> entry.get("tag").asString(), entry -> entry));
    }

    /** Each named area's tags, keyed by its name. */
    private static Map<String, List<String>> areaTags(McpSyncClient client) {
        return nodes(read(client, "namedArea.list", Map.of("slug", SLUG))).stream()
                .collect(Collectors.toMap(area -> area.get("name").asString(), area -> strings(area.get("tags"))));
    }

    private static JsonNode onlySuggestion(McpSyncClient client) {
        var suggestions = read(client, "tag.suggestions", Map.of("slug", SLUG));
        assertThat(suggestions).hasSize(1);
        return suggestions.get(0);
    }

    private static JsonNode usageOf(JsonNode suggestion, String tag) {
        var side = suggestion.get("tagA").get("tag").asString().equals(tag) ? "tagA" : "tagB";
        return suggestion.get(side).get("usage");
    }

    private List<String> tagsOfArtifact(String title) {
        return jdbc.queryForList("SELECT t.tag FROM artifact_tag t JOIN artifact a ON a.id = t.artifact_id "
                + "WHERE a.organization_id = ? AND a.title = ?", String.class, ORG, title);
    }

    private static List<JsonNode> nodes(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static List<String> strings(JsonNode array) {
        return nodes(array).stream().map(JsonNode::asString).toList();
    }

    private static JsonNode read(McpSyncClient client, String tool, Map<String, Object> args) {
        return JSON.readTree(call(client, tool, args));
    }

    private static String call(McpSyncClient client, String tool, Map<String, Object> args) {
        var result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        var text = textOf(result);
        assertThat(result.isError()).as("%s must succeed: %s", tool, text).isNotEqualTo(Boolean.TRUE);
        return text;
    }

    private static String refusal(McpSyncClient client, String tool, Map<String, Object> args) {
        var result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        var text = textOf(result);
        assertThat(result.isError()).as("%s must be refused: %s", tool, text).isTrue();
        return text;
    }

    private static String textOf(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(c -> ((McpSchema.TextContent) c).text()).collect(Collectors.joining(" "));
    }

    private McpSyncClient connect(String bearer) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp")
                .httpRequestCustomizer(
                        (builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + bearer))
                .build();
        return McpClient.sync(transport).clientInfo(new McpSchema.Implementation("axonia-tags-e2e", "1.0.0")).build();
    }

    private String mint(String user, Set<String> scopes) {
        return tokens.mint(new MintPersonalAccessToken(user, ORG, "e2e-" + user, scopes, null));
    }

    private void seedUser(String userId) {
        jdbc.update(
                "INSERT INTO identity_user (organization_id, user_id, display_name, email, active, scim_id, arrival, "
                        + "created_at, updated_at, version) VALUES (?, ?, ?, null, true, ?, 'SYNCED', now(), now(), 0)",
                ORG, userId, "Agent " + userId, "scim-" + userId);
    }

    private void seedCommunity(String slug, Long kindId) {
        jdbc.update(
                "INSERT INTO communities (organization_id, slug, name, description, status, kind_id, created_at, "
                        + "updated_at, version) VALUES (?, ?, ?, '', 'ACTIVE', ?, now(), now(), 0)",
                ORG, slug, slug, kindId);
    }

    private void seedMembership(String slug, String userId, String role) {
        jdbc.update("INSERT INTO memberships (organization_id, community_id, user_id, role, joined_at, updated_at, "
                + "version) VALUES (?, (SELECT id FROM communities WHERE organization_id = ? AND slug = ?), ?, ?, "
                + "now(), now(), 0)", ORG, ORG, slug, userId, role);
    }
}
