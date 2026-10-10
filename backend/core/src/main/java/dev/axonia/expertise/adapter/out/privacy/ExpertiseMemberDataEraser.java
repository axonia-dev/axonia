package dev.axonia.expertise.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * Every signal, curation and declaration table keys on {@code user_id} and exists only to describe that one member's
 * expertise graph, so all five rows go with them. The vocabulary tables ({@code expertise_tag_alias},
 * {@code expertise_tag_dismissal}) hold no personal column and are untouched.
 */
@Component
@RequiredArgsConstructor
public class ExpertiseMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId());
        var deleted = jdbc.update("DELETE FROM expertise_signal WHERE organization_id = :org AND user_id = :user",
                params);
        deleted += jdbc.update("DELETE FROM expertise_hidden_tag WHERE organization_id = :org AND user_id = :user",
                params);
        deleted += jdbc.update("DELETE FROM expertise_opt_out WHERE organization_id = :org AND user_id = :user",
                params);
        deleted += jdbc.update(
                "DELETE FROM expertise_dismissed_signal WHERE organization_id = :org AND user_id = :user", params);
        deleted += jdbc.update("DELETE FROM expertise_declaration WHERE organization_id = :org AND user_id = :user",
                params);
        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
