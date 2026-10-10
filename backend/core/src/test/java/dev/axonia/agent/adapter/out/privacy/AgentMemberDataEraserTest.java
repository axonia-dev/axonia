package dev.axonia.agent.adapter.out.privacy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.FootprintTestSupport;
import dev.axonia.shared.api.personaldata.MemberErasure;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemberDataEraserTest extends FootprintTestSupport {

    private static final String ORG = "default";
    private static final String TARGET = "target-user";
    private static final String NEIGHBOUR = "neighbour-user";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AgentMemberDataEraser eraser;

    @Test
    void deletesOnlyTheTargetsToolCallsAndSparesTheNeighbour() {

        seedToolCall(TARGET);
        seedToolCall(NEIGHBOUR);

        var counts = eraser.erase(MemberErasure.builder().organizationId(ORG).userId(TARGET)
                .surrogateId("erased:test-surrogate").erasedAt(Instant.parse("2026-07-27T10:00:00Z")).build());

        assertThat(counts.deleted()).isEqualTo(1);
        assertThat(countCalls(TARGET)).isZero();
        assertThat(countCalls(NEIGHBOUR)).isEqualTo(1);
    }

    private void seedToolCall(String userId) {

        jdbc.update(
                "INSERT INTO agent_tool_call (organization_id, user_id, token_id, tool_name, outcome, "
                        + "occurred_at) VALUES (:org, :user, 1, 'topic.propose', 'OK', now())",
                Map.of("org", ORG, "user", userId));
    }

    private long countCalls(String userId) {

        return jdbc.queryForObject(
                "SELECT count(*) FROM agent_tool_call WHERE organization_id = :org AND user_id = :user",
                Map.of("org", ORG, "user", userId), Long.class);
    }
}
