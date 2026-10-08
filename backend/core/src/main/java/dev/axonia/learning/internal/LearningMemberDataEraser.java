package dev.axonia.learning.internal;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ErasureCounts;
import dev.axonia.MemberDataEraser;
import dev.axonia.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * Every intent the member named in the Organization goes with them. Leaving each community on the way to erasure
 * deletes the intents there first; this deletes whatever that path did not reach.
 */
@Component
@RequiredArgsConstructor
public class LearningMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var deleted = jdbc.update("DELETE FROM learning_intent WHERE organization_id = :org AND user_id = :user",
                Map.of("org", erasure.organizationId(), "user", erasure.userId()));
        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
