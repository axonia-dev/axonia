package dev.axonia.agent.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/** A tool call is logged against the token holder who made it, so the row goes with them. */
@Component
@RequiredArgsConstructor
public class AgentMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId());
        var deleted = jdbc.update("DELETE FROM agent_tool_call WHERE organization_id = :org AND user_id = :user",
                params);
        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
