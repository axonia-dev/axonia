package dev.axonia.agent.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.ExportScope;
import dev.axonia.FootprintTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPersonalDataExporterTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AgentPersonalDataExporter exporter;

    @Test
    void memberScopedExportCarriesTheMembersOwnCallsAndNoOneElses() {

        seedCall(TARGET, "target-tool-call", 1);
        seedCall(NEIGHBOUR, "neighbour-tool-call", 1);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        assertThat(fragment.fileName()).isEqualTo("agent.json");
        var rows = rowsOf(fragment, "agent_tool_call");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("user_id", TARGET).containsEntry("tool_name", "target-tool-call");
    }

    @Test
    void organizationScopedExportCarriesEveryMembersCalls() {

        seedCall(TARGET, "target-tool-call", 1);
        seedCall(NEIGHBOUR, "neighbour-tool-call", 1);

        var fragment = exporter.export(new ExportScope.WholeOrganization(ORG));

        assertThat(rowsOf(fragment, "agent_tool_call")).hasSize(2);
    }

    /**
     * A raw token id means nothing to the person reading their own export - the join resolves it to the token's name.
     */
    @Test
    void memberScopedExportResolvesTheTokenToItsName() {

        var tokenId = seedToken(TARGET, "My Laptop");
        seedCall(TARGET, "target-tool-call", tokenId);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "agent_tool_call");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("token", "My Laptop"));
    }

    /**
     * A token that no longer exists - revoked and purged, or never seeded - still leaves the call itself exportable.
     */
    @Test
    void memberScopedExportCarriesTheCallWithANullTokenWhenTheTokenIsGone() {

        seedCall(TARGET, "target-tool-call", 1);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "agent_tool_call");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("token", null));
    }

    /**
     * A raw connection id means nothing to the person reading their own export - the join resolves it to the
     * connection's own client name.
     */
    @Test
    void memberScopedExportResolvesTheConnectionToItsClientName() {

        var connectionId = seedConnection(TARGET, "Claude Code");
        seedCallWithConnection(TARGET, "target-tool-call", connectionId);

        var fragment = exporter.export(new ExportScope.OneMember(ORG, TARGET));

        var rows = rowsOf(fragment, "agent_tool_call");
        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("connection", "Claude Code"));
    }

    private void seedCall(String user, String toolName, long tokenId) {

        jdbc.update(
                "INSERT INTO agent_tool_call (organization_id, user_id, token_id, tool_name, outcome, occurred_at) "
                        + "VALUES (:org, :user, :token, :tool, 'SUCCESS', now())",
                Map.of("org", ORG, "user", user, "token", tokenId, "tool", toolName));
    }

    private void seedCallWithConnection(String user, String toolName, long connectionId) {

        jdbc.update(
                "INSERT INTO agent_tool_call (organization_id, user_id, connection_id, tool_name, outcome, "
                        + "occurred_at) VALUES (:org, :user, :connection, :tool, 'SUCCESS', now())",
                Map.of("org", ORG, "user", user, "connection", connectionId, "tool", toolName));
    }

    private Long seedToken(String user, String name) {

        return jdbc.queryForObject(
                "INSERT INTO personal_access_token (organization_id, user_id, token_hash, name, created_at, "
                        + "updated_at, version) VALUES (:org, :user, :hash, :name, now(), now(), 0) RETURNING id",
                Map.of("org", ORG, "user", user, "hash", "hash-" + name, "name", name), Long.class);
    }

    private Long seedConnection(String user, String clientName) {

        return jdbc
                .queryForObject(
                        "INSERT INTO agent_connection (organization_id, user_id, client_id, client_name, client_host, "
                                + "scopes, resource, redirect_uri, code_challenge, activated_at, ends_at, created_at, "
                                + "updated_at, version) VALUES (:org, :user, 'https://claude.ai/cc', :clientName, "
                                + "'claude.ai', 'read', 'https://axonia.test/mcp', 'http://localhost/callback', "
                                + "'exporter-test-challenge', now(), now() + interval '1 day', now(), now(), 0) "
                                + "RETURNING id",
                        Map.of("org", ORG, "user", user, "clientName", clientName), Long.class);
    }
}
