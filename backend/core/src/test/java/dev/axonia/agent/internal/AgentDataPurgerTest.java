package dev.axonia.agent.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDataPurgerTest extends PurgerTestSupport {

    @Autowired
    AgentDataPurger sut;
    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    void purge_removesAuditForOrg() {
        jdbc.getJdbcTemplate().execute("SET app.current_org = 'default'");
        jdbc.getJdbcTemplate().execute("INSERT INTO agent_tool_call (organization_id, user_id, token_id, tool_name,"
                + " outcome, occurred_at) VALUES ('default','u-1',1,'topic.propose','OK', now())");

        sut.purge("default");

        assertThat(jdbc.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM agent_tool_call WHERE organization_id='default'", Integer.class))
                .isZero();
    }
}
